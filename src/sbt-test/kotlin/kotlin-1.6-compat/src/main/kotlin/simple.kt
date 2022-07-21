package demo

// Test some Kotlin 1.6 features
@Target(AnnotationTarget.TYPE_PARAMETER)
annotation class BoxContent

class Box<@BoxContent T> {}

fun main() {
  // T is inferred as String because SomeImplementation derives from SomeClass<String>
  val s = Runner.run<SomeImplementation, _>()
  assert(s == "Test")

  // T is inferred as Int because OtherImplementation derives from SomeClass<Int>
  val n = Runner.run<OtherImplementation, _>()
  assert(n == 42)
}
