/*
 * Copyright (c) 2016, FPS BOSA
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

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;

/**
 * Converts a CSV file with a specific format to an RDF / SKOS file
 * 
 * The first line must contain the following column headers:
 * - "ID": unique ID
 * - "parent": parent ID (optional)
 * - language tag (e.g. "nl", "fr", "de", "en" .... one language per column)
 * - start date
 * - end date
 * 
 * @author Bart Hanssens
 */
public class Main {	
	private static List<String[]> rows;
	private static String[] header;
		
	private static String baseURI;
	
	private static final ValueFactory F = SimpleValueFactory.getInstance();
	private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy");

	private static final String START = "https://schema.org/startDate";
	private static final String END = "https://schema.org/endDate";
	
	private static final List<String> LANGS = 
			List.of("nl", "fr", "de", "en");
	private static final List<String> ALT_LANGS = 
			List.of("alt_nl", "alt_fr", "alt_de", "alt_en");
	private static final List<String> DEF_LANGS = 
			List.of("def_nl", "def_fr", "def_de", "def_en");
	private static final List<String> SCOPE_LANGS = 
			List.of("scope_nl", "scope_fr", "scope_de", "scope_en");
		
	private static final Map<String, IRI> PROPS = Map.of(
		OWL.SAMEAS.getLocalName().toLowerCase(), OWL.SAMEAS,
		SKOS.EXACT_MATCH.getLocalName().toLowerCase(), SKOS.EXACT_MATCH,
		SKOS.CLOSE_MATCH.getLocalName().toLowerCase(), SKOS.CLOSE_MATCH,
		SKOS.BROAD_MATCH.getLocalName().toLowerCase(), SKOS.BROAD_MATCH,
		SKOS.NARROW_MATCH.getLocalName().toLowerCase(), SKOS.NARROW_MATCH);

	
	/**
	 * Read CSV input file (using ; as separator).
	 * 
	 * @param f CSV file
	 * @throws IOException
	 * @throws CsvException
	 */
	private static void readCSV(File f) throws IOException, CsvException {
		CSVParser parser = new CSVParserBuilder().withSeparator(';').withIgnoreQuotations(true).build();
		
		try(Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
			CSVReader csv = new CSVReaderBuilder(r).withSkipLines(0).withCSVParser(parser).build()) {
			rows = csv.readAll();
			header = rows.remove(0);
		}
	}

	/**
	 * Write a single file
	 * 
	 * @param fmt RDF format
	 * @param f file to write to
	 * @param m model
	 * @throws java.io.IOException
	 */
	private static void writeFile(RDFFormat fmt, File f, Model m) throws IOException {
		Writer out = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
		
		RDFWriter w = Rio.createWriter(fmt, out);
		w.set(BasicWriterSettings.PRETTY_PRINT, true);
		w.startRDF();
		w.handleNamespace(SKOS.PREFIX, SKOS.NAMESPACE);
		m.forEach(w::handleStatement);
		w.endRDF();
	}
	
	/**
	 * Write SKOS files to a directory.
	 * 
	 * @param dir top level directory
	 * @param fmt format
	 * @param ext file extension
	 * @throws IOException
	 */
	private static void writeSkos(File dir, RDFFormat fmt, String ext) throws IOException {
		Model M = new LinkedHashModel();

		String vocab = baseURI.endsWith("/") 
				? baseURI.substring(0, baseURI.length()-1)
				: baseURI;	
		
		IRI scheme = F.createIRI(vocab);
		M.add(scheme, RDF.TYPE, SKOS.CONCEPT_SCHEME);
		
		boolean hasNotation = Arrays.asList(header).contains("code");
		
		for(String[] row: rows) {
			String id = row[0].replace(".", "_");
			IRI node = F.createIRI(baseURI, id);
		
			// parent or not
			if (row[1].isEmpty()) {
				M.add(node, SKOS.TOP_CONCEPT_OF, scheme);
				M.add(scheme, SKOS.HAS_TOP_CONCEPT, node);
			} else { 
				IRI parent = F.createIRI(baseURI, row[1]);
				M.add(node, SKOS.BROADER, parent);
				M.add(parent, SKOS.NARROWER, node);
			}
			
			M.add(node, RDF.TYPE, SKOS.CONCEPT);
			M.add(node, SKOS.IN_SCHEME, scheme);
			
			if (!hasNotation) {
				M.add(node, SKOS.NOTATION, F.createLiteral(row[0]));
			}
			
			for (int i = 2; i < header.length; i++) {
				if (row[i].isEmpty()) {
					continue;
				}

				if (header[i].equals("code")) {
					M.add(node, SKOS.NOTATION, F.createLiteral(row[i].trim()));
				}

				// pref labels in different languages
				if (LANGS.contains(header[i])) {
					Literal label = F.createLiteral(row[i], header[i]);
					M.add(node, SKOS.PREF_LABEL, label);
					continue;
				}
				// alt labels in different languages
				if (ALT_LANGS.contains(header[i])) {
					Literal label = F.createLiteral(row[i], header[i].replace("alt_", ""));
					M.add(node, SKOS.ALT_LABEL, label);
					continue;
				}
				// definition in different languages
				if (DEF_LANGS.contains(header[i])) {
					Literal label = F.createLiteral(row[i], header[i].replace("def_", ""));
					M.add(node, SKOS.DEFINITION, label);
					continue;
				}
				// scope notes in different languages
				if (SCOPE_LANGS.contains(header[i])) {
					Literal label = F.createLiteral(row[i], header[i].replace("scope_", ""));
					M.add(node, SKOS.SCOPE_NOTE, label);
					continue;
				}
				// optional start / end date
				if (header[i].equals("start")) {
					try {
						Date start = df.parse(row[i]);
						Literal date = F.createLiteral(start);
						M.add(node, F.createIRI(START), date);
					} catch (ParseException pe) {
						//
					}
					continue;
				}
				if (header[i].equals("end")) {
					try {
						Date end = df.parse(row[i]);
						Literal date = F.createLiteral(end);
						M.add(node,  F.createIRI(END), date);
					} catch (ParseException pe) {
					//
					}
					continue;
				}
				
				// Check skos exact/narrow match etc
				for (String prop: PROPS.keySet()) {
					if (header[i].startsWith(prop)) {
						IRI ref = F.createIRI(row[i]);
						M.add(node, PROPS.get(prop), ref);
						break;
					}
				}
				
				if (header[i].startsWith("http")) {
					M.add(node, F.createIRI(header[i]), F.createLiteral(row[i]));
				}
			}
		}
		
		File index = new File(dir, "skos." + ext);
		writeFile(fmt, index, M);
	}
	
	/**
	 * Write SKOS files, in different formats, to a directory.
	 * 
	 * @param dir top level directory 
	 * @throws java.io.IOException 
	 */
	public static void writeSkos(File dir) throws IOException {
		writeSkos(dir, RDFFormat.NTRIPLES, "nt");
		writeSkos(dir, RDFFormat.TURTLE, "ttl");
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
		} catch (IOException|CsvException ex) {
			System.err.println("Failed to read input file " + ex.getMessage());
			System.exit(-2);
		}
		
		File dir = new File(args[1]);
		if (! dir.exists()) {
			dir.mkdir();
		}
		
		try {
			writeSkos(dir);
		} catch (IOException ex) {
			System.err.println("Failed to write output");
			System.exit(-3);
		}
	}
}
