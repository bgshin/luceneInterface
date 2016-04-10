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
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;





/**
 * @author Bonggun Shin ({@code bonggun.shin@emory.edu}).
 */
public class IrqaQuery {
    static  public IndexWriter writer;

    public IrqaQuery(){
    }

    public static void makeIndexWriter(String indexPath, String stopPath, String sim) throws IOException {
        System.out.println("[makeIndexWriter] started");
        System.out.println("[makeIndexWriter]"+stopPath);
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        Analyzer analyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(mygetStopwords(stopPath)));
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);


        if (sim.equals("TFIDF"))
            iwc.setSimilarity(new ClassicSimilarity());
        else if (sim.equals("BM25"))
            iwc.setSimilarity(new BM25Similarity());
        else
            iwc.setSimilarity(new BM25Similarity());


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

    public static List<Document> query(String index, String stoppath, String question, int numResult, String sim)  throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        Analyzer analyzer = new EnglishAnalyzer(
                StopFilter.makeStopSet(mygetStopwords(stoppath)));

        if (sim.equals("TFIDF"))
            searcher.setSimilarity(new ClassicSimilarity());
        else if (sim.equals("BM25"))
            searcher.setSimilarity(new BM25Similarity());
        else
            searcher.setSimilarity(new BM25Similarity());

        String field = "contents";
        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(parser.escape(question));

        TopDocs results = searcher.search(query, numResult);
        ScoreDoc[] hits = results.scoreDocs;
        List<Document> docs = new ArrayList<Document>();

        int numTotalHits = results.totalHits;
//        System.out.println(numTotalHits + " total matching documents");

        int end = Math.min(numTotalHits, numResult);

        String searchResult = "";
//        System.out.println("Only results 1 - " + hits.length);


        for (int i = 0; i < end; i++) {
            Document doc = searcher.doc(hits[i].doc);
            docs.add(doc);
        }

        return docs;
    }



    public static void batch_query(String basedir, String indexpath) throws Exception  {

        indexpath = basedir+indexpath;
        String stopwords=basedir+"/stopwords.txt";
        IrqaQuery lp = new IrqaQuery();

        JSONParser parser = new JSONParser();
        JSONArray questions = (JSONArray) parser.parse(new FileReader(basedir+"/data/questions.json"));

        long startTime = System.currentTimeMillis();
        int answercount=0;
        int questioncount = 0;
        for (Object o : questions)
        {
            JSONObject q = (JSONObject) o;

            String query = (String) q.get("question");
            String gold_id = (String) q.get("paragraph_id");

            List<Document> docs = lp.query(indexpath,stopwords, query, 5, "BM25");


            questioncount++;
            for (Document d:docs) {
                String docid = d.get("docid");

                if (docid.equals(gold_id)) {
//                    System.out.println(docid);
                    answercount = answercount+1;
                    break;
                }
            }
            if (questioncount%1000==0) {
                long midtime = System.currentTimeMillis() - startTime;

                System.out.format("[%d] midtime=%f\n", questioncount, midtime / 1000.0);
            }
        }
        System.out.format("acc=%f\n", answercount*1.0/questioncount*100);
        long estimatedTime = System.currentTimeMillis() - startTime;
        System.out.println(estimatedTime/1000.0);
    }


    public static void get_sentence_from_json(JSONArray raw_list, String question, String docid,
                                              BufferedWriter out) throws Exception  {
        for (Object o : raw_list) {
            JSONObject rl = (JSONObject) o;

            String query = (String) rl.get("question");
            String pid = (String) rl.get("paragraph_id");

            if (query.compareTo(question)==0) {

            }

            if (pid.compareTo(docid)==0 && query.compareTo(question)==0) {
                // if docid is matched
                // get candidate index
                List<Integer> candidate_list = new ArrayList<Integer>();
//                if (rl.get("candidates").toString().length()>1){
//                System.out.println(rl.get("candidates").toString());
                String[] candidates = rl.get("candidates").toString().split(",");
                for (String cand : candidates) {
//                    System.out.println(cand);
//                    System.out.println(rl.get("candidates").toString());
                    candidate_list.add(Integer.parseInt(cand.replace(" ",""))-1);
                }
//                }

                // print with candidate 0/1
                int index_of_sen=0;
                for (Object sen : (JSONArray) rl.get("sentences")) {
                    int zero_one=0;
                    for (int cand:candidate_list) {
                        if (index_of_sen==cand)
                            zero_one = 1;
                    }
                    String out_format = String.format("%s\t%s\t%d\n", question, sen, zero_one);
                    out.write(out_format);
                    index_of_sen++;
                }
            }
            else {
                // print with candidate 0
                for (Object sen : (JSONArray) rl.get("sentences")) {
//                    System.out.format("%s\t%s\t%d\n", question, sen, 0);
                    String out_format = String.format("%s\t%s\t%d\n", question, sen, 0);
                    out.write(out_format);
                }
            }

        }
    }


    public static void pipeline(String basedir, String set, JSONObject lookup_sent) throws Exception {
        System.out.println(set + " started...");
//        String index = basedir+"/index/";
//        String index = basedir+"/index_all2/";
//        String index = basedir+"/index_all_2048/";
//        String index = basedir+"/index_all_1024/";
//        String index = basedir+"/index_all_512/";
//        String index = basedir+"/index_all_256/";
//        String index = basedir+"/index_all_128/";
//        String index = basedir+"/index_all_64/";
        String index = basedir+"/index_all_32/";
//        String index = basedir+"/index_all_16/";
//        String index = basedir+"/index_all_8/";

        String stopwords=basedir+"/stopwords.txt";
        IrqaQuery lp = new IrqaQuery();


        String answer_filename =
                String.format(basedir+"/stats/data_for_analysis/newTACL/%s_raw_list.json", set);
        String file = String.format(basedir+"/stats/data_for_analysis/newTACL/WikiQASent-%s.txt", set);

//        String lookup_8kfn = basedir+"/data/wikilookup_8k.json";
        String documents2_fn = basedir+"/data/documents2.json";

        JSONParser parser = new JSONParser();
        JSONArray answer_list = (JSONArray) parser.parse(new FileReader(answer_filename));



//        Object obj2 = parser.parse(new FileReader(lookup_8kfn));
//        JSONObject lookup_8k = (JSONObject) obj2;

        Object obj3 = parser.parse(new FileReader(documents2_fn));
        JSONArray documents2 = (JSONArray) obj3;



        List<String> questions = new ArrayList<>();


        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)));

        BufferedWriter outfile = new BufferedWriter(new FileWriter("new_validate.txt"));

        int numline=0;

        ArrayList<ArrayList<String>> sentlistAll = new ArrayList<ArrayList<String>>();
        ArrayList<ArrayList<String>> alistAll = new ArrayList<ArrayList<String>>();

        try {
            String r;
            String cquestion = "";

            ArrayList<String> sentlist  = new ArrayList<>();
            ArrayList<String> alist  = new ArrayList<>();

            while ((r = br.readLine()) != null) {
                numline++;
                String[] line = r.split("\t");

                if (cquestion.compareTo(line[0])!=0) {
                    if (cquestion.compareTo("")!=0) {
                        sentlistAll.add(sentlist);
                        alistAll.add(alist);
                        questions.add(cquestion);
                    }

                    sentlist  = new ArrayList<>();
                    alist  = new ArrayList<>();
                    sentlist.add(line[1]);
                    alist.add(line[2]);

                    cquestion = line[0];
                }
                else {
                    sentlist.add(line[1]);
                    alist.add(line[2]);
                }
            }

            sentlistAll.add(sentlist);
            alistAll.add(alist);
            questions.add(cquestion);


        } finally {
            br.close();
        }

        System.out.println(questions.size());

        for (int i=0;i<questions.size();i++){
            String query = questions.get(i);
            List<Document> docs = lp.query(index,stopwords, query, 5, "BM25");
//            Object o = (Object) answer_list.get(0);
            JSONObject rl = (JSONObject) answer_list.get(i);
            String gold_pid =(String) rl.get("paragraph_id");
//            String gold_q =(String) rl.get("question");




//            if (gold_q.compareTo(query)!=0)
//                System.out.println(i);

            for (Document d:docs) {
                String docid = d.get("docid");

                if (gold_pid.compareTo(docid)==0) {
//                    get sentences from gold (alistAll, sentlistAll)
                    for (int j=0;j<sentlistAll.get(i).size();j++) {
                        if (sentlistAll.get(i).get(j).length()<1 ||
                                sentlistAll.get(i).get(j).compareTo(" ")==0 ||
                                sentlistAll.get(i).get(j).compareTo("  ")==0 ||
                                sentlistAll.get(i).get(j).compareTo("''")==0 ||
                                sentlistAll.get(i).get(j).compareTo("   ")==0
                                )
                            continue;
//                        System.out.printf("%s\t%s\t%s\n", query, sentlistAll.get(i).get(j), alistAll.get(i).get(j));
                    }
                }
                else {
//                    get_sentence_from_lookup();
//                    lookup_sent.get(docid)
                    JSONArray sents = (JSONArray) lookup_sent.get("Timeline_of_classical_mechanics-Abstract");

                    if (sents==null) {
                        System.out.println("noway, "+docid + "\n");
                    }
                    else {
                        for (int kk=0; kk<sents.size(); kk++) {
                            if (sents.get(kk).toString().length()<1 ||
                                    sents.get(kk).toString().compareTo(" ")==0 ||
                                    sents.get(kk).toString().compareTo("  ")==0 ||
                                    sents.get(kk).toString().compareTo("''")==0 ||
                                    sents.get(kk).toString().compareTo("   ")==0
                                    )
                                continue;
                            System.out.printf("%s\t%s\t%s\n", query, sents.get(kk).toString(), "0");
//                            System.out.println(sents.get(kk));
                        }
                    }
                }
            }
        }



        outfile.close();


//        System.out.println(raw_list.size());
        System.out.println(numline);
    }



    /** Simple command-line based search demo. */
    public static void main(String[] args) throws Exception {
//        String basedir = "/Users/bong/works/research/irqa";
        String basedir = "/home/bgshin/works/irqa";

        List<String> exps = new ArrayList<>();

        exps.add("/index_all_2048/");
        exps.add("/index_all_1024/");
        exps.add("/index_all_512/");
        exps.add("/index_all_256/");
        exps.add("/index_all_128/");
        exps.add("/index_all_64/");
        exps.add("/index_all_32/");
        exps.add("/index_all_16/");
        exps.add("/index_all_8/");
        exps.add("/index_all_4/");
        exps.add("/index_all_2/");

        String path = "";
//        String index = "/Users/bong/works/research/irqa/index/";
//        String index = "/Users/bong/works/research/irqa/index_all2/";
//        String index = "/Users/bong/works/research/irqa/index_all_2048/";
//        String index = "/Users/bong/works/research/irqa/index_all_1024/";
//        String index = "/Users/bong/works/research/irqa/index_all_512/";
//        String index = "/Users/bong/works/research/irqa/index_all_256/";
//        String index = "/Users/bong/works/research/irqa/index_all_128/";
//        String index = "/Users/bong/works/research/irqa/index_all_64/";
//        String index = "/Users/bong/works/research/irqa/index_all_32/";
//        String index = "/Users/bong/works/research/irqa/index_all_16/";
//        String index = "/Users/bong/works/research/irqa/index_all_8/";
//        String index = "/Users/bong/works/research/irqa/index_all_4/";
        String index = "/Users/bong/works/research/irqa/index_all_2/";

        for (int i=0; i<exps.size(); i++) {
            String indexpath = exps.get(i);
            batch_query(basedir,indexpath);
        }


//        String basedir = "/Users/bong/works/research/irqa";
//
//        JSONParser parser = new JSONParser();
//        String lookup_sentfn = basedir+"/data/wikilookup3_sentence.json";
//        Object obj1 = parser.parse(new FileReader(lookup_sentfn));
//        JSONObject lookup_sent = (JSONObject) obj1;
//
//        pipeline(basedir, "dev", lookup_sent);
//        pipeline(basedir, "test", lookup_sent);
//        pipeline(basedir, "train", lookup_sent);
    }
}

