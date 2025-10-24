# Examples

Directory of end-to-end use case examples.

<!-- Please update this list when adding a new example / keep it in alphabetical order -->
- [Newsletter Drafter](newsletter-drafter/README.md): Human-in-the-loop Agentic Workflow example with LangChain4j.

## How to add new examples

When contributing to this directory, the project must be self-contained. 
A user must be able to copy and paste the `pom.xml` file and be able to reuse it on his projects.

1. Create the sub module project via `mvn quarkus:create` and commit the project as it's generated.
2. Add the project as a sub module in the main `examples/pom.xml` file, but DO NOT add the examples project as parent.
3. Document your example with a detailed README.md file. DO NOT commit the default Quarkus readme.
4. Add your example to the list in the section above.
