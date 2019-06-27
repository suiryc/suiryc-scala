package suiryc.scala

// Annotation to make 'unused' warnings disappear.
// See: https://github.com/scala/bug/issues/10790
// TODO: Extending 'deprecated' is deprecated in scala 2.13.
// To replace by native scala.annotation.unused annotation.
// See: https://github.com/scala/scala/pull/7623
// Temporary workaround:
//class unused extends scala.annotation.unused
// scalastyle:off class.name
class unused extends deprecated("unused", "")
// scalastyle:on class.name
