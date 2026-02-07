package examples;

import org.eclipse.daanse.tooling.docgen.api.annotation.DocExample;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocStep;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocOutput;

@DocExample(
    title = "Sample Example",
    description = "A sample example for testing extraction.",
    tags = {"test", "sample"},
    order = 1,
    group = "Testing"
)
public class SampleDocExample {

    @DocStep(order = 1, title = "First Step",
             description = "This is the first step.")
    public void stepOne() {
        String greeting = "Hello";
        System.out.println(greeting);
    }

    @DocStep(order = 2, title = "Second Step",
             description = "This is the second step.")
    @DocOutput("Result: 42")
    public void stepTwo() {
        int result = 42;
        System.out.println("Result: " + result);
    }
}
