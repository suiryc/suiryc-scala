package suiryc.scala.sbt

import sbtassembly.*
import sbtassembly.Assembly.{Dependency, JarEntry}

object AssemblyEx {

  // Notes on assembly plugin:
  // In version 1.x, unique temporary folders were created for each source
  // (project, or external dependency), containing a copy of the content of the
  // source.
  // Since version 2.x, content is read on-the-fly: files are accessed as an
  // input stream either from real file or jar entry.
  //
  // The plugin gathers all files with the same target path (relatively to the
  // root folder) and use assemblyMergeStrategy to determine what to do: it
  // returns a MergeStrategy handling all the gathered files.
  // Some specific files have a default associated strategy: e.g. configuration
  // files are concatenated. For other files, the default is to deduplicate,
  // which only works if there is either only one file, or if all duplicates
  // have the same content.

  private type Dependencies = Vector[Dependency]

  object Filters {

    // Dummy filter used to print details.
    val trace: Dependencies => Dependencies = { conflicts =>
      val lines = List(s"target=${conflicts.head.target}") ++
        conflicts.map { conflict =>
          s"  origin=${
            conflict.module.map { module =>
              s"${module.organization}:${module.name}:${module.version}"
            }.getOrElse {
              "project"
            }
          }  source=${conflict.source}"
        }
      // scalastyle:off token
      println(lines.mkString("\n"))
      // scalastyle:on token
      conflicts
    }

    // Filter by source: only (external) libraries.
    val libsOnly: Dependencies => Dependencies = { conflicts =>
      conflicts.filterNot(_.isProjectDependency)
    }

  }

  val filters: Filters.type = Filters

  object Strategies {

    // Uses the original default strategy.
    // Works even if there are no more files on input (which may happend due to
    // filtering).
    val original: MergeStrategy = CustomMergeStrategy("original") { conflicts =>
      if (conflicts.isEmpty) Right(Vector.empty)
      else MergeStrategy.defaultMergeStrategy(conflicts.head.source)(conflicts)
    }

    // Dummy strategy used to print details.
    val trace: MergeStrategy = CustomMergeStrategy("trace") { conflicts =>
      original(filters.trace(conflicts))
    }

    // Strategy used to concat files in order depending on source: libraries
    // then project.
    // This is useful when libraries use application.conf, either to override
    // other libraries settings, or mistakenly instead of reference.conf.
    // The actual application needs to have its application.conf override others'
    // which is 'easy' when listing jar dependencies in classpath (order them by
    // importance), but more complicated or unpredictable when using default
    // assembly behaviour.
    val concatLibsThenProject: MergeStrategy = CustomMergeStrategy("concatLibsThenProject") { conflicts =>
      // If there is only one file, just use it.
      if (conflicts.length == 1) Right(Vector(JarEntry(conflicts.head.target, conflicts.head.stream)))
      else {
        // Otherwise concat libraries files then project files.
        val (inProject, inLibs) = conflicts.partition(_.isProjectDependency)
        MergeStrategy.concat.apply(inLibs ++ inProject)
      }
    }

    // Keep only (external) libraries files, and use original strategy.
    val libsOnly: MergeStrategy = CustomMergeStrategy("libsOnly") { conflicts =>
      original(filters.libsOnly(conflicts))
    }

    // Extend MergeStrategy to handle filtering dependencies.
    implicit class MergeStrategyEx(strat: MergeStrategy) {
      def filtered(filter: Dependencies => Dependencies): MergeStrategy =
        CustomMergeStrategy(strat.name, strat.notifyThreshold) { conflicts =>
          strat(filter(conflicts))
        }
    }

  }

  val strategies: Strategies.type = Strategies

}
