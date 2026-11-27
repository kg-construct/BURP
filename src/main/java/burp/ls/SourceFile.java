package burp.ls;

import burp.model.PlanNode;
import burp.model.key.AstKey;
import burp.reporting.StatementParts;
import burp.util.Util;

import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface SourceFile extends PlanNode {
    @Override
    default @Nullable Resource getIri() {
        return null;
    }

    File getFile(List<StatementParts> fileOriginStmts);

    class Local implements SourceFile {
        private final String path;
        private PlanNode parent = null;

        public Local(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        @Override
        public PlanNode getParent() {
            return parent;
        }

        @Override
        public void setParent(PlanNode parent) {
            this.parent = parent;
        }

        @Override
        public AstKey computeAstKey() {
            return new AstKey.SourceFileKey(path != null ? path : "");
        }

        @Override
        public File getFile(List<StatementParts> fileOriginStmts) {
            return new File(path);
        }

        @Override
        public String toString() {
            return "Local(" + path + ")";
        }
    }

    class Remote implements SourceFile {
        private final String url;
        private String downloadedPath = null;
        private PlanNode parent = null;

        public Remote(String url) {
            this.url = url;
        }

        public Remote(String url, String downloadedPath) {
            this.url = url;
            this.downloadedPath = downloadedPath;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public PlanNode getParent() {
            return parent;
        }

        @Override
        public void setParent(PlanNode parent) {
            this.parent = parent;
        }

        @Override
        public AstKey computeAstKey() {
            return new AstKey.SourceFileKey(url != null ? url : "");
        }

        @Override
        public File getFile(List<StatementParts> fileOriginStmts) {
            if (downloadedPath == null) {
                downloadedPath = Util.downloadFile(url);
            }
            return new File(downloadedPath);
        }

        @Override
        public String toString() {
            return "Remote(" + url + " dowloaded=" + downloadedPath + ")";
        }
    }
}
