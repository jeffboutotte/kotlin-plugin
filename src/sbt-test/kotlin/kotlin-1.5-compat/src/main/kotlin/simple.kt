package demo

sealed interface Polygon

value class Password(val s: String)

// Test some Kotlin 1.5 features
fun main() {
  // T is inferred as String because SomeImplementation derives from SomeClass<String>
  val s = Runner.run<SomeImplementation, _>()
  assert(s == "Test")

  // T is inferred as Int because OtherImplementation derives from SomeClass<Int>
  val n = Runner.run<OtherImplementation, _>()
  assert(n == 42)
}
