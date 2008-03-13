package org.esa.beam.framework.gpf.graph;

import com.bc.ceres.util.TemplateReader;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.xml.XppDomReader;
import com.thoughtworks.xstream.io.xml.XppDomWriter;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;

import java.io.Reader;
import java.io.Writer;
import java.util.Map;

/**
 * The {@link GraphIO} class contains methods for the
 * serialization/deserialization of XML-based {@link Graph} definitions.
 *
 * @author Maximilian Aulinger
 * @author Norman Fomferra
 * @author Ralf Quast
 */
public class GraphIO {

    /**
     * Serializes the given {@code graph} into XML
     *
     * @param graph  the {@code graph} to write into XML
     * @param writer the writer to use for serialization.
     */
    public static void write(Graph graph, Writer writer) {
        XStream xStream = initXstream();
        xStream.toXML(graph, writer);
    }

    /**
     * Deserializes a {@code graph} from an XML Reader.
     *
     * @param reader the readerto use for deserialization
     * @return the deserialized <code>graph</code>
     * @throws GraphException 
     */
    public static Graph read(Reader reader) throws GraphException {
        return read(reader, null);
    }


    /**
     * Deserializes a {@link Graph} from a XML Reader using a mapping
     * for the substitution of template variables inside the XML-based
     * {@link Graph} definition.
     *
     * @param reader    the XML reader
     * @param variables a mapping from template variable names to their string values.
     * @return the deserialized <code>graph</code>
     * @throws GraphException 
     */
    public static Graph read(Reader reader, Map<String, String> variables) throws GraphException {
        XStream xStream = initXstream();
        Reader inputReader = reader;
        if (variables != null) {
            inputReader = new TemplateReader(reader, variables);
        }
        // todo - throw exception if a given variable does not exist in the graph definition or if variables are not set.
        Graph graph = (Graph) xStream.fromXML(inputReader);
        if (graph.getVersion() == null) {
            throw new GraphException("No version is specified in the graph xml.");
        } else if (!Graph.CURRENT_VERSION.equals(graph.getVersion())) {
            throw new GraphException("Wrong version given in the graph xml. " +
            		"Given version: " + graph.getVersion() + " Current version: "+Graph.CURRENT_VERSION);
        }
        return graph;
    }

    /**
     * Creates and initializes the underlying serialization tool XStream.
     *
     * @return an initilalized instance of {@link XStream}
     */
    private static XStream initXstream() {
        XStream xStream = new XStream();
        xStream.setClassLoader(GraphIO.class.getClassLoader());

        xStream.alias("graph", Graph.class);
        xStream.addImplicitCollection(Graph.class, "nodeList", Node.class);

        xStream.alias("node", Node.class);
        xStream.aliasField("operator", Node.class, "operatorName");
        xStream.useAttributeFor("id", String.class);

        xStream.alias("sources", Node.SourceList.class);
        xStream.aliasField("sources", Node.class, "sourceList");
        xStream.registerConverter(new SourceListConverter());

        xStream.alias("parameters", Xpp3Dom.class);
        xStream.aliasField("parameters", Node.class, "configuration");
        xStream.registerConverter(new Xpp3DomConverter());

        return xStream;
    }

    /**
     * Constructor. Private, in order to prevent instantiation.
     */
    private GraphIO() {

    }

    private static class Xpp3DomConverter implements Converter {

        public boolean canConvert(Class aClass) {
            return Xpp3Dom.class.equals(aClass);
        }

        public void marshal(Object object, HierarchicalStreamWriter hierarchicalStreamWriter,
                            MarshallingContext marshallingContext) {
            Xpp3Dom configuration = (Xpp3Dom) object;
            Xpp3Dom[] children = configuration.getChildren();
            for (Xpp3Dom child : children) {
                HierarchicalStreamCopier copier = new HierarchicalStreamCopier();
                XppDomReader source = new XppDomReader(child);
                copier.copy(source, hierarchicalStreamWriter);
            }

        }

        public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader,
                                UnmarshallingContext unmarshallingContext) {
            HierarchicalStreamCopier copier = new HierarchicalStreamCopier();
            XppDomWriter xppDomWriter = new XppDomWriter();
            copier.copy(hierarchicalStreamReader, xppDomWriter);
            return xppDomWriter.getConfiguration();
        }
    }

    private static class SourceListConverter implements Converter {

        public boolean canConvert(Class aClass) {
            return Node.SourceList.class.equals(aClass);
        }

        public void marshal(Object object, HierarchicalStreamWriter hierarchicalStreamWriter,
                            MarshallingContext marshallingContext) {
            Node.SourceList sourceList = (Node.SourceList) object;
            NodeSource[] sources = sourceList.getSources();
            for (NodeSource source : sources) {
                hierarchicalStreamWriter.startNode(source.getName());
                hierarchicalStreamWriter.setValue(source.getSourceNodeId());
                hierarchicalStreamWriter.endNode();
            }
        }

        public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader,
                                UnmarshallingContext unmarshallingContext) {
            Node.SourceList sourceList = new Node.SourceList();
            while (hierarchicalStreamReader.hasMoreChildren()) {
                hierarchicalStreamReader.moveDown();
                String name = hierarchicalStreamReader.getNodeName();
                String sourceNodeId = hierarchicalStreamReader.getValue().trim();
                sourceList.addSource(new NodeSource(name, sourceNodeId));
                hierarchicalStreamReader.moveUp();
            }
            return sourceList;
        }
    }

}
