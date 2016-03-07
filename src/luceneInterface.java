/**
 * Copyright 2016, Emory University
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Bonggun Shin ({@code bonggun.shin@emory.edu}).
 */
public class luceneInterface {
    static  public IndexWriter writer;

    public luceneInterface(){
    }

    public static void makeIndexWriter(String indexPath, String stopPath) throws IOException {
        System.out.println("[makeIndexWriter] started");
        System.out.println("[makeIndexWriter]"+stopPath);
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        Analyzer analyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(mygetStopwords(stopPath)));
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(dir, iwc);
    }


    public static void indexDoc(String docid, String... args) throws IOException {
        //        docid, title, contents,...
        Document doc = new Document();

        Field pathField = new StringField("docid", docid, Field.Store.YES);
        doc.add(pathField);

        for (int i = 0; i < args.length; i+=2) {
            String field = args[i];
            String field_text = args[i + 1];
            doc.add(new TextField(field, field_text, Field.Store.NO));
//            System.out.println("[doc.add]" + path + ":" + field + ":" + field_text);
        }

        System.out.println("adding " + docid);
        writer.addDocument(doc);
    }

    public static List<String> mygetStopwords(String stopFile) {
        List<String> stopwords = new ArrayList<>();
        String line;

        try (FileReader fr = new FileReader(stopFile);
             BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    public static List<Document> query2(String index, String stoppath, String question)  throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        Analyzer analyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(mygetStopwords(stoppath)));

        searcher.setSimilarity(new BM25Similarity());
        String field = "contents";
        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(parser.escape(question));

        TopDocs results = searcher.search(query, 5);
        ScoreDoc[] hits = results.scoreDocs;
        List<Document> docs = new ArrayList<Document>();

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int end = Math.min(numTotalHits, 5);

        String searchResult = "";
        System.out.println("Only results 1 - " + hits.length);


        for (int i = 0; i < end; i++) {
            Document doc = searcher.doc(hits[i].doc);
            docs.add(doc);
        }

        return docs;
    }

    public static  void query(String index, String stoppath, String question, int numResult)  throws Exception  {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        Analyzer analyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(mygetStopwords(stoppath)));

        searcher.setSimilarity(new BM25Similarity());
        String field = "contents";
        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(parser.escape(question));

        TopDocs results = searcher.search(query, numResult);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int end = Math.min(numTotalHits, numResult);

        String searchResult="";
        System.out.println("Only results 1 - " + hits.length);

        for (int i = 0; i < end; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("docid");

            if (path != null) {
                searchResult = question+"\t"+path + "\t"
                        + (i+1) + "\t" + hits[i].score;
                System.out.println(searchResult);
            } else {
                searchResult = (i+1) + ". " + "No path for this document";
                System.out.println(searchResult);
            }

        }

    }


    /** Simple command-line based search demo. */
    public static void main(String[] args) throws Exception {

        String path = "";
        String index = "./index/";
        String stopwords="/Users/bong/IdeaProjects/irp1/src/stopwords.txt";
        luceneInterface lp = new luceneInterface();

        lp.makeIndexWriter(index,stopwords);
        lp.indexDoc("abc", "title",  "static void", "contents",  "static void world");
        lp.indexDoc("efg", "title",  "public int", "contents",  "public int world");
        writer.close();

        lp.query(index,stopwords,"static", 5);

    }
}

