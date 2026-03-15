package com.arxivanalysis;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * ArxivMapper
 *
 * Reads one JSON line per call (the ArXiv metadata snapshot uses JSON Lines
 * format — one complete JSON object per line).
 *
 * For each paper the categories field may contain multiple space-separated
 * category codes (e.g. "cs.LG cs.AI math.ST").  The mapper emits one
 * key-value pair PER CATEGORY so a single paper contributes to every
 * category it belongs to (many-to-many mapping).
 *
 * Output key:   category code  (e.g. "cs.LG")
 * Output value: tab-separated raw counts  "1\tversions\tabstractWords\tauthors"
 *               where all fields are integers — this format is safe to use
 *               with a combiner because it is always a sum, never an average.
 */
public class ArxivMapper extends Mapper<LongWritable, Text, Text, Text> {

    // Counter group name used for monitoring data-quality issues
    private static final String COUNTER_GROUP = "ArxivMapper";

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {

        String line = value.toString().trim();
        if (line.isEmpty()) {
            return;
        }

        JSONObject paper;
        try {
            paper = new JSONObject(line);
        } catch (JSONException e) {
            // Malformed JSON — skip this line and track it for monitoring
            context.getCounter(COUNTER_GROUP, "MALFORMED_JSON").increment(1);
            return;
        }

        // --- Extract categories -----------------------------------------------
        // optString returns "" if the field is missing or null, never throws
        String categoriesRaw = paper.optString("categories", "").trim();
        if (categoriesRaw.isEmpty()) {
            context.getCounter(COUNTER_GROUP, "MISSING_CATEGORIES").increment(1);
            return;
        }
        String[] categories = categoriesRaw.split("\\s+");

        // --- Extract number of versions ---------------------------------------
        // versions is a JSONArray: [{"version":"v1","created":"..."}, ...]
        JSONArray versionsArray = paper.optJSONArray("versions");
        int versionCount = (versionsArray != null) ? versionsArray.length() : 1;
        // Default to 1 — every paper must have at least one version

        // --- Extract abstract word count -------------------------------------
        String abstractText = paper.optString("abstract", "").trim();
        int abstractWords = 0;
        if (!abstractText.isEmpty()) {
            // Split on any whitespace sequence; -1 keeps trailing empty tokens
            // so the count is accurate even for abstracts with multiple spaces
            abstractWords = abstractText.split("\\s+").length;
        }

        // --- Extract author count --------------------------------------------
        // authors_parsed is a JSONArray of arrays: [["LastName","FirstName",""], ...]
        // Some very old entries omit this field entirely.
        JSONArray authorsArray = paper.optJSONArray("authors_parsed");
        int authorCount = (authorsArray != null) ? authorsArray.length() : 0;

        // Validate: skip papers with clearly corrupt data
        if (versionCount < 1 || abstractWords < 0 || authorCount < 0) {
            context.getCounter(COUNTER_GROUP, "INVALID_FIELDS").increment(1);
            return;
        }

        // --- Emit one record per category ------------------------------------
        // Value format: count TAB versionCount TAB abstractWords TAB authorCount
        String emitValue = "1\t" + versionCount + "\t" + abstractWords + "\t" + authorCount;
        Text outputValue = new Text(emitValue);

        for (String category : categories) {
            category = category.trim();
            if (!category.isEmpty()) {
                context.write(new Text(category), outputValue);
            }
        }
    }
}
