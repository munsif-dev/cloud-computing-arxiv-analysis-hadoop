package com.arxivanalysis;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * ArxivReducer — also registered as the Combiner in ArxivAnalysis.
 *
 * WHY THIS IS SAFE AS A COMBINER
 * --------------------------------
 * The mapper always emits raw integer counts (never averages).  The reducer
 * sums these partial sums identically regardless of how many times it runs
 * as a combiner on intermediate data.  The final division that produces
 * averages happens ONLY at the very end of the reduce phase — never inside
 * the combiner invocation.  This is the standard "sum-then-divide" pattern
 * that makes combiner reuse correct for average computations.
 *
 * Input key:    category code (e.g. "cs.LG")
 * Input values: iterable of "count\tversions\tabstractWords\tauthors" strings
 *
 * Output key:   category code
 * Output value: human-readable tab-separated metrics string, e.g.
 *   "total_papers=152341  avg_versions=1.87  avg_abstract_words=142  avg_authors=3.2"
 */
public class ArxivReducer extends Reducer<Text, Text, Text, Text> {

    private static final String COUNTER_GROUP = "ArxivReducer";

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {

        long totalPapers      = 0;
        long sumVersions      = 0;
        long sumAbstractWords = 0;
        long sumAuthors       = 0;

        for (Text val : values) {
            String[] parts = val.toString().split("\t", -1);
            if (parts.length < 4) {
                context.getCounter(COUNTER_GROUP, "MALFORMED_VALUES").increment(1);
                continue;
            }
            try {
                totalPapers      += Long.parseLong(parts[0]);
                sumVersions      += Long.parseLong(parts[1]);
                sumAbstractWords += Long.parseLong(parts[2]);
                sumAuthors       += Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                context.getCounter(COUNTER_GROUP, "PARSE_ERRORS").increment(1);
            }
        }

        // Skip categories with no valid records (defensive guard)
        if (totalPapers == 0) {
            return;
        }

        double avgVersions      = (double) sumVersions      / totalPapers;
        double avgAbstractWords = (double) sumAbstractWords / totalPapers;
        double avgAuthors       = (double) sumAuthors       / totalPapers;

        String result = String.format(
            "total_papers=%-8d\tavg_versions=%.2f\tavg_abstract_words=%.0f\tavg_authors=%.1f",
            totalPapers, avgVersions, avgAbstractWords, avgAuthors
        );

        context.write(key, new Text(result));
    }
}
