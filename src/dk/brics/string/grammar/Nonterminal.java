package dk.brics.string.grammar;

import dk.brics.string.directedgraph.GraphNode;

import java.util.LinkedList;
import java.util.List;

/**
 * Grammar nonterminal.
 */
public class Nonterminal implements GraphNode, Comparable<Nonterminal> {

    private List<Production> productions = new LinkedList<Production>();

    private int key;

    private String alias;

    private boolean taint;

    public Nonterminal(int key) {
        this(key, "");
    }

    public Nonterminal(int key, String alias) {
        this.key = key;
        this.alias = alias;
    }

    public boolean isTaint() {
        return taint;
    }

    public void setTaint(boolean taint) {
        this.taint = taint;
    }

    /**
     * Returns the key of this nonterminal.
     */
    public int getKey() {
        return key;
    }

    /**
     * Returns the alias of this nonterminal.
     */
    public String alias() {
        return alias;
    }

    /**
     * Returns the productions of this nonterminal.
     */
    public List<Production> getProductions() {
        return productions;
    }

    /**
     * Sets the productions of this nonterminal.
     */
    public void setProductions(List<Production> p) {
        productions = p;
    }

    public int compareTo(Nonterminal n) {
        return n.key - key;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns name of this nonterminal.
     */
    @Override
    public String toString() {
        if (alias == null || alias.equals(""))
            return "x" + key;
        return alias + "k" + key;
    }
}
