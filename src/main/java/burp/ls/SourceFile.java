package burp.ls;

import burp.model.PlanNode;
import burp.reporting.StatementParts;
import burp.util.Util;

import java.io.File;
import java.util.Collections;
import java.util.List;

public interface SourceFile extends PlanNode {
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
        public Iterable<PlanNode> children() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<PlanNode> dependencies() {
            return Collections.emptyList();
        }

        @Override
        public File getFile(List<StatementParts> fileOriginStmts) {
            return new File(path);
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

        public String getDownloadedPath() {
            return downloadedPath;
        }

        public void setDownloadedPath(String downloadedPath) {
            this.downloadedPath = downloadedPath;
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
        public Iterable<PlanNode> children() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<PlanNode> dependencies() {
            return Collections.emptyList();
        }

        @Override
        public File getFile(List<StatementParts> fileOriginStmts) {
            if (downloadedPath == null) {
                downloadedPath = Util.downloadFile(url);
            }
            return downloadedPath != null ? new File(downloadedPath) : null;
        }
    }
}
