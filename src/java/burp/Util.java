package burp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.Resource;

import burp.vocabularies.RML;

public class Util {

	/**
	 * Translate a string into its IRI safe value as per R2RML's steps
	 * 
	 * @param string
	 * @return
	 */
	public static String toIRISafe(String string) {
		// The IRI-safe version of a string is obtained by applying the following 
		// transformation to any character that is not in the iunreserved 
		// production in [RFC3987].
		StringBuffer sb = new StringBuffer();
		for(char c : string.toCharArray()) {
			if(inIUNRESERVED(c)) sb.append(c);
			else sb.append('%' + Integer.toHexString((int) c).toUpperCase());
		}
		return sb.toString();
	}
	
	/**
	 *	Check whether the characters are part of iunreserved as per
	 *  https://tools.ietf.org/html/rfc3987#section-2.2
	 */
	private static boolean inIUNRESERVED(char c) {
		if("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".indexOf(c) != -1) return true;
		else if (c >= 160 && c <= 55295) return true;
		else if (c >= 63744 && c <= 64975) return true;
		else if (c >= 65008 && c <= 65519) return true;
		else if (c >= 65536 && c <= 131069) return true;
		else if (c >= 131072 && c <= 196605) return true;
		else if (c >= 196608 && c <= 262141) return true;
		else if (c >= 262144 && c <= 327677) return true;
		else if (c >= 327680 && c <= 393213) return true;
		else if (c >= 393216 && c <= 458749) return true;
		else if (c >= 458752 && c <= 524285) return true;
		else if (c >= 524288 && c <= 589821) return true;
		else if (c >= 589824 && c <= 655357) return true;
		else if (c >= 655360 && c <= 720893) return true;
		else if (c >= 720896 && c <= 786429) return true;
		else if (c >= 786432 && c <= 851965) return true;
		else if (c >= 851968 && c <= 917501) return true;
		else if (c >= 921600 && c <= 983037) return true;
		return false;
	}

	/**
	 * Converts a byte array into a Hex string
	 * Code based on https://www.programiz.com/java-programming/examples/convert-byte-array-hexadecimal
	 */
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHexString(byte[] o) {
		byte[] bytes = (byte[]) o;
		char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
	}
	
	public static boolean isAbsoluteAndValidIRI(String string) {
		IRI iri = IRIFactory.iriImplementation().create(string.toString());
		return iri.isAbsolute() && !iri.hasViolation(true);
	}
	
	public static boolean isAbsolute(String string) {
		IRI iri = IRIFactory.iriImplementation().create(string.toString());
		return iri.isAbsolute();
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
		} catch(Exception e) {
			throw new RuntimeException("Problem downloading " + url);
		}
	}

	public static String getDecompressedFile(String file, Resource compression) {
		try {
			if(RML.none.equals(compression))
				return file;
			
			String temp = Files.createTempFile(null, ".extracted.tmp").toString();
			
			OutputStream out = new FileOutputStream(temp);
			FileInputStream fin = new FileInputStream(file);
			InputStream in = null;
			
			if(RML.zip.equals(compression)) {
				in = new ZipInputStream(fin);
			} else if(RML.gzip.equals(compression)) {
				in = new GzipCompressorInputStream(fin);
			} else if(RML.targz.equals(compression)) {
				TarArchiveInputStream a = new TarArchiveInputStream(fin);
				a.getNextEntry();
				in = a;
			} else if(RML.tarxz.equals(compression)) {
				TarArchiveInputStream a = new TarArchiveInputStream(fin);
				a.getNextEntry();
				in = a;
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
	
}