package finance.tools;

/**
 * Creator of the Factory Method pattern. The template method newTool() calls the abstract factoryMethod(),
 * which each concrete creator overrides to return its own Tool product.
 */
public abstract class ToolCreator {
    protected abstract Tool factoryMethod();

    public Tool newTool() { return factoryMethod(); }
}
