package burp.util;

import burp.reporting.BurpException;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.jena.atlas.io.OutputUtils;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.iri.Violation;
import org.apache.jena.iri.ViolationCodes;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rfc3986.Chars3986;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class Util {

    public static String toIRISafe(String string) {
        StringBuilder sb = new StringBuilder();
        for (char c : string.toCharArray()) {
            if (Chars3986.iunreserved(c)) sb.append(c);
            else sb.append('%').append(Integer.toHexString(c).toUpperCase(Locale.getDefault()));
        }
        return sb.toString();
    }

    public static String toURISafe(String string) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if (Chars3986.unreserved(c)) {
                sb.append(c);
            } else {
                sb.append("%");
                OutputUtils.printHex(sb, b & 0xFF, 2);
            }
        }
        return sb.toString();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) return null;
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean isValidAndAbsoluteIRI(String string) {
        return isValidAndAbsolute(string, IRIFactory.iriImplementation());
    }

    public static boolean isValidAndAbsoluteURI(String string) {
        return isValidAndAbsolute(string, IRIFactory.uriImplementation());
    }

    private static boolean isValidAndAbsolute(String string, IRIFactory factory) {
        IRI iri = factory.create(string);
        Iterator<Violation> violations = iri.violations(false);
        boolean hasViolations = false;
        while (violations.hasNext()) {
            Violation violation = violations.next();
            if (violation.getViolationCode() != ViolationCodes.LOWERCASE_PREFERRED) {
                hasViolations = true;
                break;
            }
        }
        return !hasViolations && iri.getScheme() != null && !iri.getScheme().trim().isEmpty();
    }

    public static boolean isAbsoluteAndValidIRI(String string) {
        return isValidAndAbsoluteIRI(string);
    }

    public static boolean isAbsoluteAndValidURI(String string) {
        return isValidAndAbsoluteURI(string);
    }

    public static boolean isAbsolute(String string) {
        return URI.create(string.toLowerCase()).isAbsolute();
    }

    public static String downloadFile(String url) {
        try {
            String temp = Files.createTempFile(null, ".download.tmp").toString();

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<InputStream> response = HttpClient
                    .newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
            FileOutputStream output = new FileOutputStream(temp);
            output.write(response.body().readAllBytes());
            output.close();

            return temp;
        } catch (Exception e) {
            throw new RuntimeException("Problem downloading " + url);
        }
    }

    public static String getDecompressedFile(String file, Resource compression) {
        try {
            if (RML.none.equals(compression))
                return file;

            String temp = Files.createTempFile(null, ".extracted.tmp").toString();

            OutputStream out = new FileOutputStream(temp);
            FileInputStream fin = new FileInputStream(file);
            InputStream in = null;

            if (RML.zip.equals(compression)) {
                ZipInputStream a = new ZipInputStream(fin);
                a.getNextEntry();
                in = a;
            } else if (RML.gzip.equals(compression)) {
                in = new GzipCompressorInputStream(fin);
            } else if (RML.targz.equals(compression)) { // FIXME Ontology specifies targzip not targz.
                TarArchiveInputStream a = new TarArchiveInputStream(new GzipCompressorInputStream(fin));
                a.getNextEntry();
                in = a;
            } else if (RML.tarxz.equals(compression)) {
                TarArchiveInputStream a = new TarArchiveInputStream(new XZCompressorInputStream(fin));
                a.getNextEntry();
                in = a;
            } else {
                throw new BurpException(new RmlError("Compression format not supported " + compression, null, RER.SourceAccessError, null, Map.of(RML.compression, compression, RML.path, file)));
            }

            IOUtils.copy(in, out);
            in.close();
            out.close();

            return temp;

        } catch (Exception e) {
            System.err.println(compression);
            throw new RuntimeException("Error decompressing file");
        }
    }

    private static void writeTarEntry(File file, TarArchiveOutputStream tos, String[] suffixes, Consumer<OutputStream> writeAction) throws IOException {
        String entryName = file.getName();
        for (String suffix : suffixes) {
            if (entryName.endsWith(suffix)) {
                entryName = entryName.substring(0, entryName.length() - suffix.length());
            }
        }
        TarArchiveEntry entry = new TarArchiveEntry(entryName);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeAction.accept(baos);
        byte[] bytes = baos.toByteArray();

        entry.setSize(bytes.length);
        tos.putArchiveEntry(entry);
        tos.write(bytes);
        tos.closeArchiveEntry();
    }

    public static void writeCompressedFile(File file, Resource compression, Consumer<OutputStream> writeAction) {
        try {
            if (compression == null || RML.none.equals(compression)) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    writeAction.accept(out);
                }
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                if (RML.zip.equals(compression)) {
                    try (ZipOutputStream zos = new ZipOutputStream(fos)) {
                        String entryName = file.getName();
                        if (entryName.endsWith(".zip")) entryName = entryName.substring(0, entryName.length() - 4);
                        zos.putNextEntry(new ZipEntry(entryName));
                        writeAction.accept(zos);
                        zos.closeEntry();
                    }
                } else if (RML.gzip.equals(compression)) {
                    try (GzipCompressorOutputStream gos = new GzipCompressorOutputStream(fos)) {
                        writeAction.accept(gos);
                    }
                } else if (RML.targz.equals(compression)) {
                    try (GzipCompressorOutputStream gos = new GzipCompressorOutputStream(fos);
                         TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
                        writeTarEntry(file, tos, new String[]{".tar.gz", ".tgz"}, writeAction);
                    }
                } else if (RML.tarxz.equals(compression)) {
                    try (XZCompressorOutputStream xos = new XZCompressorOutputStream(fos);
                         TarArchiveOutputStream tos = new TarArchiveOutputStream(xos)) {
                        writeTarEntry(file, tos, new String[]{".tar.xz"}, writeAction);
                    }
                } else {
                    throw new RuntimeException("Provided compression " + compression + " not supported.");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}