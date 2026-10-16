package burp.dev;

import burp.Main;
import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.selector.*;
import burp.model.RdfStatement;
import burp.model.RmlEvaluationResult;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.util.BURPConfiguration;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@NullMarked
public class DevServer {

    public static int runServerMode(BURPConfiguration conf) {
        List<ServerSocket> openSockets = new ArrayList<>();
        List<Thread> listenerThreads = new ArrayList<>();

        try {
            if (conf.isDevServer()) {
                ServerSocket devSocket = createServerSocket(conf.devPort);
                openSockets.add(devSocket);
                Thread devThread = new Thread(() -> startDevListener(devSocket));
                devThread.setName("BurpDevServer-Listener");
                devThread.start();
                listenerThreads.add(devThread);
            }

        } catch (Exception e) {
            System.err.println("Configuration Error starting servers: " + e.getMessage());
            
            for (ServerSocket socket : openSockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
            
            Main.reportConfigurationError(
                "Port is unavailable or socket failed: " + e.getMessage(),
                conf.reportFile,
                e
            );
            return 1;
        }

        for (Thread t : listenerThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Server listener interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        return 0;
    }

    private static ServerSocket createServerSocket(Integer portOption) throws IOException {
        int port = portOption == -1 ? 0 : portOption;
        ServerSocket socket = new ServerSocket(port);
        System.out.println("Dev Server listening on port: " + socket.getLocalPort());
        return socket;
    }

    private static final ExecutorService sharedExecutor = 
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("BurpServer-SharedExecutor");
            t.setDaemon(true);
            return t;
        });

    public static void configureGson(GsonBuilder builder) {
        builder.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(RmlEvaluationResult.class, "type")
            .registerSubtype(RdfStatement.class, "statement")
            .registerSubtype(burp.model.RdfStatementSubjectGraph.class, "statementSubjectGraph")
            .registerSubtype(burp.model.RdfPredicateObject.class, "predicateObject")
            .registerSubtype(IRITerm.class, "uri")
            .registerSubtype(LiteralTerm.class, "literal")
            .registerSubtype(BlankNodeTerm.class, "bnode")
            .registerSubtype(burp.model.RawValue.class, "raw")
            .registerSubtype(burp.model.rdf.RdfListTerm.class, "list")
            .registerSubtype(burp.model.rdf.RdfAltTerm.class, "alt")
            .registerSubtype(burp.model.rdf.RdfBagTerm.class, "bag")
            .registerSubtype(burp.model.rdf.RdfSeqTerm.class, "seq"));
        builder.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(StructureSelector.class, "type")
            .registerSubtype(CursorSelector.class, "cursor")
            .registerSubtype(IriSelector.class, "iri")
            .registerSubtype(IdSelector.class, "id")
            .registerSubtype(DocumentSelector.class, "document"));
        builder.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(EvaluationDataContext.class, "type")
            .registerSubtype(AllDataContext.class, "all")
            .registerSubtype(RangeDataContext.class, "range")
            .registerSubtype(RawStringCustomDataContext.class, "rawString")
            .registerSubtype(ReferenceValuesCustomDataContext.class, "referenceValues")
            .registerSubtype(MultiSourceDataContext.class, "multiSource"));

        builder.registerTypeHierarchyAdapter(org.apache.jena.rdf.model.RDFNode.class, (JsonSerializer<org.apache.jena.rdf.model.RDFNode>) (src, typeOfSrc, context) -> {
            if (src == null) return JsonNull.INSTANCE;
            if (src.isURIResource()) {
                return new JsonPrimitive(src.asResource().getURI());
            }
            if (src.isLiteral()) {
                return new JsonPrimitive(src.asLiteral().getString());
            }
            return new JsonPrimitive(src.toString());
        });

        builder.registerTypeHierarchyAdapter(org.apache.jena.graph.Node.class, (JsonSerializer<org.apache.jena.graph.Node>) (src, typeOfSrc, context) -> {
            if (src == null) return JsonNull.INSTANCE;
            if (src.isURI()) {
                return new JsonPrimitive(src.getURI());
            }
            if (src.isLiteral()) {
                return new JsonPrimitive(src.getLiteralLexicalForm());
            }
            return new JsonPrimitive(src.toString());
        });
    }

    private static void startDevListener(ServerSocket serverSocket) {
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                new Thread(() -> {
                    try {
                        RmlDevServerImpl serverImpl = new RmlDevServerImpl();
                        Launcher<burp.dev.rml.RmlLanguageClient> launcher = new Launcher.Builder<burp.dev.rml.RmlLanguageClient>()
                            .setLocalService(serverImpl)
                            .setRemoteInterface(burp.dev.rml.RmlLanguageClient.class)
                            .setInput(socket.getInputStream())
                            .setOutput(socket.getOutputStream())
                            .setExecutorService(sharedExecutor)
                            .configureGson(DevServer::configureGson)
                            .create();
                        serverImpl.connect(launcher.getRemoteProxy());
                        launcher.startListening().get();
                    } catch (Exception e) {
                        System.err.println("Dev connection error: " + e.getMessage());
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                    }
                }).start();
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                System.err.println("Dev Server socket exception: " + e.getMessage());
            }
        }
    }
}
