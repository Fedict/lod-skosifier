/*
 * Copyright (c) 2016, Bart Hanssens <bart.hanssens@fedict.be>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package be.fedict.lodtools.skosifier;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;

/**
 * Converts a CSV to a SKOS file and various RDF files
 * 
 * @author Bart.Hanssens
 */
public class Main {	
	private static List<String[]> rows;
	private static String[] header;
		
	private static String baseURI;
			
	/**
	 * Read CSV input file (using ; as separator).
	 * 
	 * The first line must contain the following column headers:
	 * - "ID": unique ID
	 * - "parent": parent ID (optional)
	 * - language tag (e.g. "nl", "fr".... one language per column)
	 * 
	 * @param f CSV file
	 * @throws FileNotFoundException
	 * @throws IOException 
	 */
	private static void readCSV(File f) throws FileNotFoundException, IOException {
		CSVReader r = new CSVReader(new FileReader(f), ';');
		rows = r.readAll();
		header = rows.remove(0);	
		r.close();
	}

	/**
	 * Write a single file
	 * 
	 * @param fmt RDF format
	 * @param f file to write to
	 */
	private static void writeFile(RDFFormat fmt, File f, Model m) throws IOException {
		Writer w = new FileWriter(f);
		
		RDFWriter out = Rio.createWriter(fmt, w);
		out.handleNamespace(SKOS.PREFIX, SKOS.NAMESPACE);
		out.startRDF();
		m.forEach(out::handleStatement);
		out.endRDF();
	}

	/**
	 * Write SKOS files to a directory.
	 * 
	 * @param dir top level directory 
	 */
	private static void writeSkos(File dir) throws IOException {
		Model M = new LinkedHashModel();
		ValueFactory F = SimpleValueFactory.getInstance();
	
		for(String[] row: rows) {
			IRI child = F.createIRI(baseURI, row[0]);
			M.add(child, RDF.TYPE, SKOS.CONCEPT);
			if (!row[1].isEmpty()) {
				IRI parent = F.createIRI(baseURI, row[1]);
				M.add(child, SKOS.BROADER, parent);
				M.add(parent, SKOS.NARROWER, child);
			}
			for (int i = 2; i < header.length; i++) {
				Literal label = F.createLiteral(row[i], header[i]);
				M.add(child, SKOS.PREF_LABEL, label);
			}
		}
		
		// Main index files
		File index = new File(dir, "index.ttl");
		writeFile(RDFFormat.TURTLE, index, M);

		// Small file per term
		File subdir = new File(dir, "ttl");
		subdir.mkdir();
		
		for (Resource subj : M.subjects()) {
			int pos = subj.stringValue().lastIndexOf("/");
			String name = subj.stringValue().substring(pos);
			
			File f = new File(subdir, name + ".ttl");
			writeFile(RDFFormat.TURTLE, f, M.filter(subj, null, null));
		}
	}
	
	/**
	 * Write to HTML
	 * 
	 * @param f
	 * @param nr
	 * @throws IOException 
	 */
	private static void writeHTMLTable(File f, List<String[]> rows) throws IOException {
		FileWriter w = new FileWriter(f);
		
		w.append("<html>\n")
			.append("<body>\n");
		
		w.append("<table>\n")
			.append("<tr>");
		
		for (String h: header) {
			w.append("<th>").append(h).append("</th>");
		}
		w.append("</tr>\n");
		
		for (String[] row: rows) {
			w.append("<tr>");
			for (String cell: row) {
				w.append("<td>").append(cell).append("</td>");
			}
			w.append("</tr>\n");
		}
	
		w.append("</table>");
		w.append("</body>");
		w.close();
	}
	
	/**
	 * Write HTML files to a directory.
	 * 
	 * @param dir top level directory 
	 */
	private static void writeHTML(File dir) throws IOException {
		// Main index files
		File index = new File(dir, "index.html");
		writeHTMLTable(index, rows);

		// Small file per term
		File subdir = new File(dir, "html");
		subdir.mkdir();
		
		for (String[] row: rows) {
			File f = new File(subdir, row[0] + ".html");
			List<String[]> r = new ArrayList<>();
			r.add(row);
			writeHTMLTable(f, r);
		}
	}
	
	/**
	 * Main
	 * 
	 * @param args 
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("Usage: SKOSifier <input.csv> <outputdir> <baseURI>");
			System.exit(-1);
		}
		
		baseURI = args[2];
		
		File f = new File(args[0]);
		try {
			readCSV(f);
		} catch (IOException ex) {
			System.err.println("Failed to read input file");
			System.exit(-2);
		}
		
		File dir = new File(args[1]);
		if (! dir.exists()) {
			dir.mkdir();
		}
		
		try {
			writeSkos(dir);
			writeHTML(dir);
		} catch (IOException ex) {
			System.err.println("Failed to write output");
			System.exit(-3);
		}
	}
}