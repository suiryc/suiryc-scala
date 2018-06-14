package suiryc.scala.sys

import com.typesafe.scalalogging.LazyLogging
import java.io.InputStream
import java.net.{InetAddress, ServerSocket, Socket}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, FileLock}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import suiryc.scala.io.{PathsEx, RichFile}

/**
 * Unique instance application handling.
 *
 * Either ensures that this instance is the first to run, or establishes a
 * communication channel between the first instance and next ones to pass
 * command arguments for execution in the first instance.
 *
 * Relies on file locking and local socket listener. See JUnique for another
 * example implementation: http://www.sauronsoftware.it/projects/junique/
 * This implementation uses similar principles, but tries to be simpler/smaller.
 *
 * Overview: a file is used as lock to check whether another instance is running
 * and its content indicates the local port to use as communication channel.
 * Next instance connects to said port, pass command arguments to the first
 * instance and waits for the return code.
 *
 * Implementation details
 * ----------------------
 * Given an application id (expected to be unique for each application), we
 * determine a filename which will be used as lock. The id is sanitized to
 * prevent disallowed characters in filename.
 * Since we cannot properly prevent other programs from deleting it, it must
 * be placed in a folder where it has little chances to be deleted by mistake.
 * By default the user home folder should be good.
 *
 * We require a lock on the first 4 bytes, in which are written the local port
 * number, then we try to lock the 5th byte. The first lock is used to ensure
 * that the first instance (if not this one) has properly written the port
 * number. The second lock is used to determine whether this instance is the
 * first one to run.
 * Note: as a reference JUnique uses a global lock file as first lock, and
 * another file (not the application lock one) to write the port number.
 *
 * If we are the first instance to run (second lock acquired), we start a local
 * socket listener, and write the local port number in the first 4 bytes of the
 * file before releasing the first lock.
 * The second lock is held until the application exits (the file is also deleted
 * right before releasing the lock).
 *
 * If we are another instance (second lock not acquired), we read the local port
 * number in the lock file (since the first lock was acquired, we are sure the
 * first instance did write it), connect to it, pass the command arguments (to
 * the first instance) and waits for the execution return code to exit with.
 *
 * Notes
 * -----
 * To keep it even simpler we only handle passing the command arguments to the
 * first running instance and exits with the return code. As a reference JUnique
 * allows to pass more than one "message" to the first instance.
 * The protocol is TLV-like where the type is pre-determined: the second
 * instance passes the arguments array (length of array, then each string as
 * length and UTF-8 bytes) and the first instance returns the result code.
 *
 * Since there was no implementation of UNIX sockets in Windows (at least
 * before Windows 10 build 17063), such kind of sockets are not generically
 * available in the JDK, hence the use of local socket server.
 *
 * There si no need to wait for server/connections to finish before exiting
 * application (in any case upon stopping we return failure code, which is also
 * what we do on the client side if socket is unexpectedly closed).
 * So set the associated threads as daemons.
 */
object UniqueInstance extends LazyLogging {

  // Some constants (to fix code style warnings)
  /** Size (bytes) needed to write an Int */
  private val INT_SIZE = 4
  /** Socket server backlog. */
  private val SERVER_BACKLOG = 10

  /** Whether we are stopping. */
  @volatile private var _stopping = false
  /** The local socket server (if any). */
  @volatile private var _server = Option.empty[ServerSocket]

  /**
   * Starts the unique instance.
   *
   * @param appId the application (unique) id
   * @param f the function used to handle command arguments
   * @param args the command arguments
   * @return the return code for handling the command arguments if this is the
   *         unique instance; other instances never return from this function
   *         but exits with the return code received from the first instance
   */
  def start(appId: String, f: Array[String] => Int, args: Array[String]): Int = {
    try {
      val lockPath = RichFile.userHome.toPath.resolve(s".${PathsEx.sanitizeFilename(appId)}")
      val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
      // Lock the region where is the local port number
      val dataLock = channel.lock(0, INT_SIZE.toLong, false)
      // The unique instance lock
      val instanceLockOpt = try {
        Option(channel.tryLock(INT_SIZE.toLong, 1, false))
      } catch {
        case _: Exception => None
      }

      instanceLockOpt match {
        case Some(instanceLock) =>
          // We are the first instance to run, holding the lock
          logger.debug(s"Unique instance starting")
          startUniqueInstance(lockPath, channel, dataLock, instanceLock, f, args)

        case None =>
          // Another instance is supposedly running
          logger.debug(s"Unique instance already running, delegating command execution")
          startOtherInstance(channel, dataLock, args)
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to start instance: ${ex.getMessage}", ex)
        sys.exit(-1)
    }
  }

  /** Stops this unique instance. */
  def stop(): Unit = {
    _stopping = true
    _server.foreach(_.close())
  }

  /** Starts the (first) unique instance. */
  private def startUniqueInstance(lockPath: Path, channel: FileChannel,
    dataLock: FileLock, instanceLock: FileLock,
    f: Array[String] => Int, args: Array[String]): Int =
  {
    // Add shutdown hook to clean resources before exiting
    sys.addShutdownHook {
      // First delete the file
      Files.delete(lockPath)
      // Release the lock (note: actually not necessary as it is automatic
      // when closing the file).
      instanceLock.release()
      // Close the file
      channel.close()
    }

    // Start a local server, and write the port in the lock file
    val server = new ServerSocket(0, SERVER_BACKLOG, InetAddress.getLoopbackAddress)
    _server = Some(server)
    val bb = ByteBuffer.allocate(INT_SIZE)
    bb.putInt(server.getLocalPort)
    bb.rewind()
    if (channel.write(bb, 0) != bb.limit) {
      throw new Exception(s"Failed to write local port in lock file")
    }
    channel.force(false)
    dataLock.release()

    // Then run our command before starting serving other instances
    val r = f(args)
    new ServerHandler(server, f).start()
    r
  }

  /** Starts a second instance. */
  private def startOtherInstance(channel: FileChannel, dataLock: FileLock, args: Array[String]): Nothing = {
    // Release the lock (we don't need it anymore) and read the local prt
    dataLock.release()
    val bb1 = ByteBuffer.wrap(new Array[Byte](INT_SIZE))
    val port = {
      if (channel.read(bb1, 0) != bb1.limit) {
        throw new Exception("Failed to read socket port to connect to unique instance")
      }
      bb1.getInt(0)
    }

    // Pass the arguments and waits for the result.
    // In any case, do not return but exits.
    try {
      val socket = new Socket(InetAddress.getLoopbackAddress, port)
      val is = socket.getInputStream
      val os = socket.getOutputStream

      // Pass the arguments
      bb1.putInt(0, args.length)
      os.write(bb1.array)
      @scala.annotation.tailrec
      def loop(args: List[String]): Unit = {
        if (args.nonEmpty) {
          val bytes = args.head.getBytes(StandardCharsets.UTF_8)
          bb1.putInt(0, bytes.length)
          os.write(bb1.array)
          os.write(bytes)
          loop(args.tail)
        }
      }
      loop(args.toList)

      // Wait for the return code to exit with
      read(is, bb1.array)
      val r = bb1.getInt(0)
      sys.exit(r)
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to execute command on unique instance: ${ex.getMessage}", ex)
        sys.exit(-1)
    }
  }

  /** Fills an array from input stream. */
  private def read(is: InputStream, array: Array[Byte]): Unit = {
    val read = is.read(array)
    if (read != array.length) {
      throw new Exception(s"Read=<$read> expected=<${array.length}>")
    }
  }

  /** Local server socket handler. */
  private class ServerHandler(server: ServerSocket, f: Array[String] => Int) extends Thread {

    setDaemon(true)

    override def run(): Unit = {
      @scala.annotation.tailrec
      def loop(): Unit = {
        val socketOpt = try {
          if (!_stopping) Some(server.accept())
          else None
        } catch {
          case _: Exception => None
        }

        socketOpt match {
          case Some(socket) =>
            new SocketHandler(socket, f).start()
            loop()

          case None =>
            if (!_stopping) loop()
        }
      }

      loop()
    }

  }

  /** Local server socket connection handler. */
  private class SocketHandler(socket: Socket, f: Array[String] => Int) extends Thread {

    setDaemon(true)

    override def run(): Unit = {
      try {
        // Read and process arguments
        val is = socket.getInputStream
        val bb1 = ByteBuffer.wrap(new Array[Byte](INT_SIZE))
        read(is, bb1.array)
        val nargs = bb1.getInt(0)

        def loop(n: Int, args: List[String]): Array[String] = {
          if (n > 0) {
            read(is, bb1.array)
            val len = bb1.getInt(0)
            val bb = ByteBuffer.wrap(new Array[Byte](len))
            read(is, bb.array)
            loop(n - 1, args :+ new String(bb.array, StandardCharsets.UTF_8))
          } else {
            args.toArray
          }
        }

        val args = loop(nargs, Nil)
        done(execute(args))
      } catch {
        case ex: Exception =>
          logger.error(s"Failed to read arguments from socket: ${ex.getMessage}")
          done(-1)
      }
    }

    private def execute(args: Array[String]): Int = {
      try {
        if (!_stopping) f(args) else -1
      } catch {
        case ex: Exception =>
          logger.error(s"Failed to process arguments: ${ex.getMessage}", ex)
          -1
      }
    }

    private def done(code: Int): Unit = {
      // Send return code before closing socket
      try {
        val os = socket.getOutputStream
        val bb = ByteBuffer.wrap(new Array[Byte](INT_SIZE))
        bb.putInt(code)
        os.write(bb.array)
      } catch {
        case ex: Exception =>
          logger.warn(s"Failed to return response code through socket: ${ex.getMessage}")
      }
      try {
        socket.close()
      } catch {
        case ex: Exception =>
          logger.warn(s"Failed to close socket: ${ex.getMessage}")
      }
    }

  }

}
