# Examples

Directory of end-to-end use case examples.

<!-- Please update this list when adding a new example / keep it in alphabetical order -->
- [Agentic + HTTP](agentic-http/README.md): Example of a workflow enriching an agent prompt from a remote HTTP request.
- [HTTP Basic Auth](http-basic-auth/README.md): Simple workflow exemplifying calling an HTTP service secured by Basic Auth.
- [LangChain4j Agentic Workflow](langchain4j-agentic-workflow/README.md): Example of agentic workflows declared with LangChain4j annotations and executed as Quarkus Flow workflows with Dev UI support.
- [Newsletter Drafter](newsletter-drafter/README.md): Human-in-the-loop Agentic Workflow example with LangChain4j.
- [Petstore OpenAPI](petstore-openapi/README.md): The famous Petstore Demo calling HTTP services via an OpenAPI specification file descriptor.

## How to add new examples

When contributing to this directory, the project must be self-contained. 
A user must be able to copy and paste the `pom.xml` file and be able to reuse it on his projects.

1. Create the sub module project via `mvn quarkus:create` and commit the project as it's generated.
2. Add the project as a sub module in the main `examples/pom.xml` file, but DO NOT add the examples project as parent.
3. Document your example with a detailed README.md file. DO NOT commit the default Quarkus readme.
4. Ensure that integration tests has `*IT` suffix and configure the failsafe plugin to run them by default.
5. Add your example to the list in the section above.
