package com.arxivanalysis;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * ArxivAnalysis — MapReduce job driver.
 *
 * Wires together the mapper, combiner, and reducer, then submits the job
 * to YARN (or local mode for quick testing).
 *
 * Usage:
 *   hadoop jar target/arxiv-analysis.jar com.arxivanalysis.ArxivAnalysis \
 *       <hdfs_input_path> <hdfs_output_path>
 *
 * Extending Configured and implementing Tool lets standard Hadoop CLI flags
 * work without any extra code, e.g.:
 *   -D mapreduce.job.reduces=2
 *   -D mapreduce.map.memory.mb=2048
 */
public class ArxivAnalysis extends Configured implements Tool {

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new ArxivAnalysis(), args);
        System.exit(exitCode);
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ArxivAnalysis <input_path> <output_path>");
            System.err.println("  input_path  : HDFS path containing the ArXiv JSON Lines file(s)");
            System.err.println("  output_path : HDFS path where results will be written (must not exist)");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "ArXiv Research Field Activity Analysis");

        // Ensure the fat JAR (with org.json bundled) is sent to all nodes
        job.setJarByClass(ArxivAnalysis.class);

        // --- Mapper / Combiner / Reducer -------------------------------------
        job.setMapperClass(ArxivMapper.class);

        job.setReducerClass(ArxivReducer.class);

        // Both mapper output key/value and reducer output key/value are Text
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Single reducer → single output file (part-r-00000).
        // The number of distinct ArXiv category codes is ~150, so one reducer
        // is more than sufficient and makes the output easy to submit/inspect.
        job.setNumReduceTasks(1);

        // --- Input / Output paths --------------------------------------------
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // Submit and wait — verbose=true prints counters to stdout on completion
        boolean success = job.waitForCompletion(true);
        return success ? 0 : 1;
    }
}
