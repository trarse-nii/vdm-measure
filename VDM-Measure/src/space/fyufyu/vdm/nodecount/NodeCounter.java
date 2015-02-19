package space.fyufyu.vdm.nodecount;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.lex.Dialect;
import org.overture.parser.lex.LexTokenReader;
import org.overture.parser.syntax.DefinitionReader;

import space.fyufyu.vdm.util.NodeInfo;
import space.fyufyu.vdm.util.NodeType;
import space.fyufyu.vdm.util.VDMPPFilenameFilter;

/**
 * Functions to count nodes of various kinds in AST and report in a a file
 * 
 * @author f-ishikawa
 *
 */
public class NodeCounter {

	/**
	 * Count nodes in VDM files and write the result in CSV files
	 * 
	 * @param targetFile
	 *            Target file of node count (can be directory to run
	 *            recursively)
	 * @param reportFilePrefix
	 *            Prefix of the file path for the result files (results will
	 *            make $PREFIX-DEF.csv, $PREFIX-EXP.csv, $PREFIX-STM.csv)
	 * @throws AnalysisException
	 * @throws IOException
	 */
	public static void analyzeAndWrite(String targetFile,
			String reportFilePrefix) throws AnalysisException, IOException {
		NodeCountReport report = analyze(new File(targetFile));
		if (report != null) {
			for (NodeType t : NodeInfo.ALL_NODE_TYPES) {
				write(report, reportFilePrefix, t);
			}
		}
	}

	/**
	 * Count nodes in VDM files
	 * 
	 * @param targetFile
	 *            Target file of node count (can be directory to run
	 *            recursively)
	 * @return Report (null if the file is not VDMPP file)
	 * @throws AnalysisException
	 */
	public static NodeCountReport analyze(File targetFile)
			throws AnalysisException, FileNotFoundException {
		if (!targetFile.exists()) {
			throw new FileNotFoundException(targetFile.getAbsolutePath());
		}
		System.out.println("Analyzing " + targetFile.getAbsolutePath());
		if (targetFile.isDirectory()) {
			File[] files = targetFile.listFiles();
			LinkedList<NodeCountReport> children = new LinkedList<>();
			for (File file : files) {
				NodeCountReport r = analyze(new File(file.getAbsolutePath()));
				if (r != null) {
					children.add(r);
				}
			}
			return new NodeCountReport(targetFile, children);
		} else {
			if (VDMPPFilenameFilter.isVDMPPFile(targetFile.getParentFile(),
					targetFile.getName())) {
				LexTokenReader reader = new LexTokenReader(targetFile,
						Dialect.VDM_PP);
				DefinitionReader defreader = new DefinitionReader(reader);
				List<PDefinition> nodes = defreader.readDefinitions();
				NodeCountVisitor visitor = new NodeCountVisitor(targetFile);
				visitor.run(nodes);
				return visitor.getReport();
			} else {
				return null;
			}
		}
	}

	/**
	 * Write result of node count to CSV files
	 * 
	 * @param reports
	 *            List of reports for target files
	 * @param reportFilePrefix
	 *            Prefix of the file path for the result files (results will
	 *            make $PREFIX-$TYPE.csv)
	 * @param nodeType
	 *            Type of the nodes (DEF, EXP or STM)
	 * @throws IOException
	 */
	private static void write(NodeCountReport report, String reportFilePrefix,
			NodeType nodeType) throws IOException {
		String path = reportFilePrefix + "-" + nodeType.toString() + ".csv";
		File f = new File(path);
		if (!f.getParentFile().exists()) {
			f.getParentFile().mkdir();
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));

		// Counter to remember the count sum for all the target files
		MapCounter rootCounter = report.getCounter(nodeType);

		// TODO: define grouping and sorting
		LinkedHashSet<String> values = new LinkedHashSet<String>(
				rootCounter.keySet());

		// Header row
		bw.write(',');
		for (String val : values) {
			bw.write(val);
			bw.write(',');
		}
		bw.write('\n');

		// Total
		bw.write("TOTAL,");
		for (String val : values) {
			int count = rootCounter.getValue(val);
			bw.write(Integer.toString(count));
			bw.write(',');
		}
		bw.write('\n');

		// Each file
		for (NodeCountReport child : report.getChildren()) {
			writeSub(child, bw, values, nodeType);
		}

		bw.close();
		System.out.println("Wrote the report to " + path);
	}

	private static void writeSub(NodeCountReport report, Writer wr, LinkedHashSet<String> values, NodeType nodeType) throws IOException {
		if(report.getTargetFile().isDirectory()){
			wr.write("[DIR]");
		}
		wr.write(report.getTargetFile().getAbsolutePath());
		wr.write(',');
		MapCounter counter = report.getCounter(nodeType);
		for (String val : values) {
			wr.write(Integer.toString(counter.getValue(val)));
			wr.write(',');
		}
		wr.write('\n');
		for (NodeCountReport child : report.getChildren()) {
			writeSub(child, wr, values, nodeType);
		}
	}

}
