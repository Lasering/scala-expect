package work.martins.simon.expect

//The execution of a fluent.Expect is delegated to core.Expect
//This means fluent.Expect does not know how to execute Expects.
//So there isn't a need to test execution of Expects in the fluent package.
//There is, however, the need to test that the core.Expect generated from a fluent.Expect is the correct one.

package object fluent
