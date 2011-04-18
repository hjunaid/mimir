/*
 *  QueryParser.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007 (also included with this distribution as file 
 *  LICENCE-AGPL3.html).
 *
 *  A commercial licence is also available for organisations whose business
 *  models preclude the adoption of open source and is subject to a licence
 *  fee charged by the University of Sheffield. Please contact the GATE team
 *  (see http://gate.ac.uk/g8/contact) if you require a commercial licence.
 *
 *  Niraj Aswani, 03 Apr 2009
 *  
 *  $Id$
 */
package gate.mimir.search.query.parser;

import gate.Annotation;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.creole.ANNIEConstants;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.OffsetComparator;

import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.SequenceQuery.Gap;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QueryParser. Given a text query, this parser returns a QueryNode object
 * that can be used for querying the Mimir index.
 */
public class QueryParser implements QueryParserConstants {

  // keeping track of row and column for gap queries
  // please note that this variable is updated only on certain occassions
  int column;
  int row;
  boolean debug = true;

  private static final OffsetComparator OFFSET_COMPARATOR = new OffsetComparator();

  /**
   * The GATE LanguageAnalyser used to tokenise plain term queries and quoted
   * strings.  Typically this will just be a tokeniser PR but we allow any
   * LanguageAnalyser so more complex processing can be injected if required.
   * The only requirement is that the LA can take an un-annotated document and
   * return one with {@link ANNIEConstants#TOKEN_ANNOTATION_TYPE} annotations
   * in the default annotation set.
   */
  private LanguageAnalyser queryTokeniser;

  /**
   * Set the GATE LanguageAnalyser used to parse quoted strings in queries.
   * This allows clients to override the default tokeniser if required, and
   * should be called before the first use of {@link #parse}.
   */
  public void setQueryTokeniser(LanguageAnalyser tok) {
    queryTokeniser = tok;
  }

  /**
   * Default query tokeniser, used if no specific tokeniser has been passed to
   * a particular parser instance.  Initialized lazily on first use.
   */
  private static LanguageAnalyser defaultQueryTokeniser;

  public static final String DEFAULT_TOKENISER_CLASS_NAME =
    "gate.creole.tokeniser.DefaultTokeniser";

  /**
   * Returns the default query tokeniser, initializing it if necessary.
   */
  private static synchronized LanguageAnalyser getDefaultQueryTokeniser() throws ParseException {
    if(defaultQueryTokeniser == null) {
      try {
        defaultQueryTokeniser = (LanguageAnalyser)Factory.createResource(
            DEFAULT_TOKENISER_CLASS_NAME);
      }
      catch(ResourceInstantiationException e) {
        throw (ParseException)new ParseException("Couldn't create query tokeniser")
          .initCause(e);
      }
    }

    return defaultQueryTokeniser;
  }

  public static void main(String[] args) throws Exception {
    String query = args[0];
    Gate.init();
    QueryNode node = QueryParser.parse(query);
    System.out.println(query);
    System.out.println(node.getClass().getName());
  }

  /**
   * Parses the query and returns a top level QueryNode object.
   * @param query the query to parse
   * @return instance of the QueryNode
   */
  public static QueryNode parse(String query) throws ParseException {
    return parse(query, getDefaultQueryTokeniser());
  }

  /**
   * Parses the query and returns a top level QueryNode object.
   * @param query the query to parse
   * @param tokeniser the GATE {@link LanguageAnalyser} used to parse strings
   *         in the query.
   * @return instance of the QueryNode
   */
  public static QueryNode parse(String query, LanguageAnalyser tokeniser) throws ParseException {
      // start parsing
      QueryParser parser = new QueryParser(new StringReader(query));
      parser.setQueryTokeniser(tokeniser);
      Query queryInst = parser.start();
      return parser.convert(queryInst, "");
  }

  /**
   * converts the given query object into an appropriate QueryNode.
   * @param queryInst
   * @return instance of QueryNode 
   */
  private QueryNode convert(Query queryInst, String space)
                                             throws ParseException {
    // need to convert queryInst into appropriate query objects
    // TermQuery, AnnotationQuery, GapQuery, InQuery, OverQuery, KleneQuery
    // OrQuery, AndQuery, SequenceQuery
    if(queryInst instanceof TermQuery) {
      return toTermQuery((TermQuery)queryInst, space);
    } else if(queryInst instanceof AnnotationQuery) {
      return toAnnotationQuery((AnnotationQuery) queryInst, space);
    } else if(queryInst instanceof GapQuery) {
      throw new ParseException(
           "Gap cannot be specified as a first or last element in the query");
    } else if(queryInst instanceof InQuery) {
      return toWithinQuery((InQuery)queryInst, space);
    } else if(queryInst instanceof OverQuery) {
      return toContainsQuery((OverQuery)queryInst, space);
    } else if(queryInst instanceof KleneQuery) {
      return toRepeatsQuery((KleneQuery)queryInst, space);
    } else if(queryInst instanceof ORQuery) {
      return toOrQuery((ORQuery)queryInst, space);
    } else if(queryInst instanceof ANDQuery) {
      return toAndQuery((ANDQuery)queryInst, space);
    } else if(queryInst instanceof SequenceQuery) {
      return toSequenceQuery((SequenceQuery)queryInst, space);
    }
    else {
      throw new ParseException("Invalid query type:" +
                                 queryInst.getClass().getName());
    }
  }

  /**
   * Conversion of the TermQuery
   * @param query
   * @return
   */
  private QueryNode toTermQuery(TermQuery query, String space) {
    if(debug) System.out.println(space + "Term="+query.index+":"+query.term);
    return new gate.mimir.search.query.TermQuery(query.index, query.term);
  }

  /**
   * Conversion of the AnnotationQuery
   * @param query
   * @return
   */
  private QueryNode toAnnotationQuery(AnnotationQuery query, String space) {
    if(debug) System.out.println(space + "Annotation="+query.type);
    List<Constraint> featureConstraints = new ArrayList<Constraint>();
    for(FeatureValuePair fvp : query.pairs) {
      Constraint c = null;
      if(fvp.constraintType == FeatureValuePair.EQ) {
        if(debug)
          System.out.println("\t"+fvp.feature+"="+fvp.value);
        c = new Constraint(ConstraintType.EQ, fvp.feature, fvp.value);
      } else if(fvp.constraintType == FeatureValuePair.REGEX) {
        if(fvp.value instanceof String[]) {
          if(debug) {
            String[] values = (String[]) fvp.value;
            System.out.println("\t"+fvp.feature+".REGEX("+values[0]+"," +
                               values[1]+")");
          }
        } else {
          if(debug) System.out.println("\t"+fvp.feature+".REGEX("
                                        +fvp.value+")");
        }
        c = new Constraint(ConstraintType.REGEX, fvp.feature, fvp.value);
      } else if(fvp.constraintType == FeatureValuePair.LT) {
        if(debug)
          System.out.println("\t"+fvp.feature+"<"+fvp.value);
        c = new Constraint(ConstraintType.LT, fvp.feature, fvp.value);
      } else if(fvp.constraintType == FeatureValuePair.GT) {
        if(debug)
          System.out.println("\t"+fvp.feature+">"+fvp.value);
        c = new Constraint(ConstraintType.GT, fvp.feature, fvp.value);
      } else if(fvp.constraintType == FeatureValuePair.LE) {
        if(debug)
          System.out.println("\t"+fvp.feature+"<="+fvp.value);
        c = new Constraint(ConstraintType.LE, fvp.feature, fvp.value);
      } else {
        if(debug)
          System.out.println("\t"+fvp.feature+">="+fvp.value);
        c = new Constraint(ConstraintType.GE, fvp.feature, fvp.value);
      }
      featureConstraints.add(c);
    }
    return new gate.mimir.search.query.AnnotationQuery(query.type,
                                                          featureConstraints);
  }

  /**
   * Conversion of the InQuery into WithinQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toWithinQuery(InQuery query, String space)
                                                 throws ParseException {
    if(debug) System.out.println(space + "WithinQuery:");
    QueryNode targetQuery = convert(query.innerQuery, space + " ");
    QueryNode surroundingQuery = convert(query.outerQuery, space + " ");
    return new gate.mimir.search.query.WithinQuery(targetQuery,
                                                    surroundingQuery);
  }

  /**
   * Conversion of the OverQuery into ContainsQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toContainsQuery(OverQuery query, String space)
                                                     throws ParseException {
    if(debug) System.out.println(space + "ContainsQuery");
    QueryNode targetQuery = convert(query.overQuery, space + " ");
    QueryNode nestedQuery = convert(query.innerQuery, space + " ");
    return new gate.mimir.search.query.ContainsQuery(targetQuery, nestedQuery);
  }

  /**
   * Conversion of the KleneQuery into RepeatsQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toRepeatsQuery(KleneQuery query, String space)
                                                     throws ParseException {
    if(debug) System.out.println(space + "Repeats:"+query.min+".."+query.max);
    QueryNode queryNode = convert(query.query, space + " ");
    return new gate.mimir.search.query.RepeatsQuery(queryNode, query.min,
                                                                   query.max);
  }

  /**
   * Conversion of the ORQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toOrQuery(ORQuery query, String space)
                                             throws ParseException {
    int totalQueries = query.queriesToOr.size();
    if(debug) System.out.println(space + "OrQuery:"+totalQueries);
    QueryNode [] orQueries = new QueryNode[totalQueries];
    for(int i=0;i<totalQueries;i++) {
      orQueries[i] = convert(query.queriesToOr.get(i), space + " ");
    }
    return new gate.mimir.search.query.OrQuery(orQueries);
  }

  /**
   * Conversion of the ANDQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toAndQuery(ANDQuery query, String space)
                                             throws ParseException {
    int totalQueries = query.queriesToAnd.size();
    if(debug) System.out.println(space + "AndQuery:"+totalQueries);
    QueryNode [] andQueries = new QueryNode[totalQueries];
    for(int i=0;i<totalQueries;i++) {
      andQueries[i] = convert(query.queriesToAnd.get(i), space + " ");
    }
    return new gate.mimir.search.query.AndQuery(andQueries);
  }


  /**
   * Conversion of the SequenceQuery
   * @param query
   * @return
   * @throws ParseException
   */
  private QueryNode toSequenceQuery(SequenceQuery query, String space)
                                                         throws ParseException {
    if(query.queriesInOrder.size() > 1) {
      if(debug) System.out.println(space +
                                 "SequenceQuery:"+query.queriesInOrder.size());
    }
    List<QueryNode> queries = new ArrayList<QueryNode>();
    List<Gap> queryGaps = new ArrayList<Gap>();

    boolean canBeGapQuery = false;

    for(int i=0;i<query.queriesInOrder.size();i++) {
      Query q = query.queriesInOrder.get(i);
      if(q instanceof GapQuery) {
        if(!canBeGapQuery) {
          throw new ParseException("Improper use of the Gap");
        }

        if(i == query.queriesInOrder.size()-1) {
          throw new ParseException(
                         "Last element in the SequenceQuery cannot be gap");
        }

        GapQuery gq = (GapQuery) q;
        Gap gap =
           gate.mimir.search.query.SequenceQuery.getGap(gq.minGap, gq.maxGap);
        if(debug) System.out.println(space +
                                     " GapQuery:"+gq.minGap+".."+gq.maxGap);
        queryGaps.add(gap);
        // next element cannot be a gap query
        canBeGapQuery = false;
        continue;
      }

      // expecting a gap?
      if(canBeGapQuery) {
        // yes but this is not a gap, so add an empty gap
        queryGaps.add(gate.mimir.search.query.SequenceQuery.getGap(0, 0));
      }
      queries.add(convert(q, query.queriesInOrder.size() > 1 ? space + " " : space));
      canBeGapQuery = true;
    }

    if(queries.size() == 1) {
      return queries.get(0);
    } else {
      return new gate.mimir.search.query.SequenceQuery(
            queryGaps.toArray(new Gap[0]), queries.toArray(new QueryNode[0]));
    }
  }

  /** converts escape sequences into normal sequences */
  public String unescape(String s) {
    return s.replaceAll("\\\"IN\\\"","IN")
                      .replaceAll("\\\"OVER\\\"","OVER")
                      .replaceAll("\\\"AND\\\"","AND")
                      .replaceAll("\\\"OR\\\"","OR")
                      .replaceAll("\\\"REGEX\\\"","REGEX")
                      .replaceAll("\\\\\\{","{").replaceAll("\\\\\\}","}")
                      .replaceAll("\\\\\\<","<").replaceAll("\\\\\\>",">")
                      .replaceAll("\\\\\\-","-")
                      .replaceAll("\\\\\\[","[").replaceAll("\\\\\\]","]")
                      .replaceAll("\\\\\\(","(").replaceAll("\\\\\\)",")")
                      .replaceAll("\\\\\\:",":").replaceAll("\\\\\\+","+")
                      .replaceAll("\\\\\\|","|").replaceAll("\\\\\\?","?")
                      .replaceAll("\\\\\\&","&")
                      .replaceAll("\\\\\\.",".").replaceAll("\\\\\"","\"")
                      .replaceAll("\\\\\\=","=").replaceAll("(\\\\){2}","\\\\");
  }

  // parse string and obtain the appropriate query object
  public Query parseString(String s) throws ParseException {
    Document document = null;
    Corpus corpus = null;
    synchronized(queryTokeniser) {
      try {
        document = Factory.newDocument(unescape(s));
        corpus = Factory.newCorpus("QueryParser corpus");
        corpus.add(document);
        queryTokeniser.setCorpus(corpus);
        queryTokeniser.setDocument(document);
        queryTokeniser.execute();

        List<Annotation> tokens = new ArrayList<Annotation>(
            document.getAnnotations().get(ANNIEConstants.TOKEN_ANNOTATION_TYPE));
        Collections.sort(tokens, OFFSET_COMPARATOR);

        if(tokens.size() > 1) {
          SequenceQuery sq = new SequenceQuery();
          for(Annotation term : tokens) {
            TermQuery tq = new TermQuery();
            tq.term = (String)term.getFeatures()
                        .get(ANNIEConstants.TOKEN_STRING_FEATURE_NAME);
            tq.index = null;  // as valy suggested using null instead of "string";
            sq.add(tq);
          }
          return sq;
        }
        else {
          TermQuery tq = new TermQuery();
          tq.term = (String)tokens.get(0).getFeatures()
                      .get(ANNIEConstants.TOKEN_STRING_FEATURE_NAME);
          tq.index = null;  // as valy suggested using null instead of "string";
          return tq;
        }
      }
      catch(ResourceInstantiationException e) {
        throw (ParseException)new ParseException(
            "Error creating GATE document for string parser").initCause(e);
      }
      catch(ExecutionException e) {
        throw (ParseException)new ParseException(
            "Error tokenising string").initCause(e);
      }
      finally {
        queryTokeniser.setCorpus(null);
        queryTokeniser.setDocument(null);
        if(corpus != null) {
          corpus.clear();
          Factory.deleteResource(corpus);
        }
        if(document != null) {
          Factory.deleteResource(document);
        }
      }
    }
  }

// lexical analyser

// starting method with EOF
  final public Query start() throws ParseException {
  Query q;
    q = QueryPlus();
    {if (true) return q;}
    jj_consume_token(0);
    throw new Error("Missing return statement in function");
  }

/**
 * Query+
 * If more than one query detected, an object of sequence query is returned. 
 * If there is only one query, that particular instance of query is returned.
 */
  final public Query QueryPlus() throws ParseException {
  Query q;
  Query q1;
  SequenceQuery sq = new SequenceQuery();
    q = Query(null);
    sq.add(q);
    label_1:
    while (true) {
      if (jj_2_1(2147483647)) {
        ;
      } else {
        break label_1;
      }
      q1 = Query(q);
      if(q1 instanceof InQuery ||
         q1 instanceof OverQuery || q1 instanceof ORQuery ||
         q1 instanceof ANDQuery || q1 instanceof KleneQuery) {
        sq.removeLastElement();
      }

      q = q1;
      sq.add(q);
    }
    if(q instanceof GapQuery)
      {if (true) throw new ParseException(
      "Gap cannot be the first/last element. See line:"+row+", column:"+column);}

    if(sq.size() == 1 && !(q instanceof InQuery ||
         q instanceof OverQuery || q instanceof ORQuery ||
         q instanceof ANDQuery || q instanceof KleneQuery))
      {if (true) return q;}
    else
      {if (true) return sq;}
    throw new Error("Missing return statement in function");
  }

/**
 * Returns true if the next token in the stream is a reserved word
 */
  final public boolean reserved() throws ParseException {
    switch (jj_nt.kind) {
    case string:
      jj_consume_token(string);
      break;
    case leftbrace:
      jj_consume_token(leftbrace);
      break;
    case leftbracket:
      jj_consume_token(leftbracket);
      break;
    case or:
      jj_consume_token(or);
      break;
    case and:
      jj_consume_token(and);
      break;
    case in:
      jj_consume_token(in);
      break;
    case over:
      jj_consume_token(over);
      break;
    case plus:
      jj_consume_token(plus);
      break;
    case tok:
      jj_consume_token(tok);
      break;
    case leftsquarebracket:
      jj_consume_token(leftsquarebracket);
      break;
    case hyphen:
      jj_consume_token(hyphen);
      break;
    case number:
      jj_consume_token(number);
   {if (true) return true;}
      break;
    default:
      jj_la1[0] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

/**
 * parse a single query.
 */
  final public Query Query(Query previousQuery) throws ParseException {
  Query q;
  Query[] qs = new Query[2];
    switch (jj_nt.kind) {
    case leftbracket:
      jj_consume_token(leftbracket);
      // a query can be surrounded by ( and )
              q = QueryPlus();
      jj_consume_token(rightbracket);
      break;
    case leftbrace:
      q = AnnotationQuery();
      break;
    case number:
    case hyphen:
    case tok:
      q = TermOrNamedIndexQuery();
      break;
    case string:
      q = QuotedTextQuery();
      break;
    case leftsquarebracket:
      q = GapQuery();
      break;
    case in:
      q = InQuery(previousQuery);
      break;
    case over:
      q = OverQuery(previousQuery);
      break;
    default:
      jj_la1[1] = jj_gen;
      if (jj_2_2(2147483647)) {
        q = OrQuery(previousQuery);
      } else if (jj_2_3(2147483647)) {
        q = AndQuery(previousQuery);
      } else {
        switch (jj_nt.kind) {
        case plus:
          q = KleneQuery(previousQuery);
          break;
        default:
          jj_la1[2] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    }
    {if (true) return q;}
    throw new Error("Missing return statement in function");
  }

/**
 * Gap. Only valid if it is a part of a sequence query.
 * Cannot appear at the start or at the end of a query
 */
  final public Query GapQuery() throws ParseException {
  GapQuery gq = new GapQuery();
  Token t;
    t = jj_consume_token(leftsquarebracket);
    column = t.beginColumn;
    row = t.beginLine;
    t = jj_consume_token(number);
   gq.maxGap = Integer.parseInt(t.image);
    switch (jj_nt.kind) {
    case period:
      jj_consume_token(period);
      jj_consume_token(period);
      t = jj_consume_token(number);
     gq.minGap = gq.maxGap; gq.maxGap = Integer.parseInt(t.image);
      break;
    default:
      jj_la1[3] = jj_gen;
      ;
    }
    jj_consume_token(rightsquarebracket);
    {if (true) return gq;}
    throw new Error("Missing return statement in function");
  }

/**
 * Returns true if the next token in the stream is a regex word
 */
  final public boolean regex() throws ParseException {
    jj_consume_token(period);
    jj_consume_token(regex);
   {if (true) return true;}
    throw new Error("Missing return statement in function");
  }

/**
 * Returns true if the next token in the stream is a double number
 */
  final public String number() throws ParseException {
  StringBuffer sb = new StringBuffer();
  Token t;
    switch (jj_nt.kind) {
    case hyphen:
      t = jj_consume_token(hyphen);
    sb.append(t.image);
      break;
    default:
      jj_la1[4] = jj_gen;
      ;
    }
    t = jj_consume_token(number);
    sb.append(t.image);
    switch (jj_nt.kind) {
    case period:
      t = jj_consume_token(period);
      sb.append(t.image);
      t = jj_consume_token(number);
      sb.append(t.image);
      break;
    default:
      jj_la1[5] = jj_gen;
      ;
    }
   {if (true) return sb.toString();}
    throw new Error("Missing return statement in function");
  }

// Regext Query
  final public void RegexQuery(AnnotationQuery aq, String feature) throws ParseException {
  Token t;
  String flags = null;
  Object value;
  int constraintType = 0;
    jj_consume_token(period);
    jj_consume_token(regex);
    jj_consume_token(leftbracket);
    t = jj_consume_token(string);
    value = t.image.substring(1, t.image.length()-1);
    constraintType = FeatureValuePair.REGEX;
    switch (jj_nt.kind) {
    case comma:
      jj_consume_token(comma);
      t = jj_consume_token(string);
      flags = t.image.substring(1, t.image.length()-1);
      value = new String[]{value.toString(), flags};
      break;
    default:
      jj_la1[6] = jj_gen;
      ;
    }
    jj_consume_token(rightbracket);
   aq.add(constraintType, feature, value, flags);
  }

/**
 * Annotation Query
 * e.g. {AnnotationType featureName=featureValue}
 * e.g. featureValue can be a quoted value
 */
  final public Query AnnotationQuery() throws ParseException {
  AnnotationQuery aq = new AnnotationQuery();
  Token t;
  String feature = null;
  Object value = null;
  String flags = null;
  String number = "";
  int constraintType = 0;
    jj_consume_token(leftbrace);
    t = jj_consume_token(tok);
    aq.type = unescape(t.image);
    label_2:
    while (true) {
      switch (jj_nt.kind) {
      case tok:
        ;
        break;
      default:
        jj_la1[7] = jj_gen;
        break label_2;
      }
      // feature name
          t = jj_consume_token(tok);
     feature = unescape(t.image);
      if (jj_2_5(2147483647)) {
        RegexQuery(aq, feature);
      } else {
        switch (jj_nt.kind) {
        case le:
        case ge:
        case lt:
        case gt:
        case equals:
          if (jj_2_4(2)) {
            t = jj_consume_token(equals);
                constraintType = FeatureValuePair.EQ;
            switch (jj_nt.kind) {
            case string:
              t = jj_consume_token(string);
                    value = t.image.substring(1, t.image.length()-1);
              break;
            case tok:
              t = jj_consume_token(tok);
                    value = unescape(t.image);
              break;
            default:
              jj_la1[8] = jj_gen;
              jj_consume_token(-1);
              throw new ParseException();
            }
          } else {
            switch (jj_nt.kind) {
            case le:
            case ge:
            case lt:
            case gt:
            case equals:
              switch (jj_nt.kind) {
              case equals:
                t = jj_consume_token(equals);
                    constraintType = FeatureValuePair.EQ;
                break;
              case le:
              case ge:
              case lt:
              case gt:
                switch (jj_nt.kind) {
                case le:
                  t = jj_consume_token(le);
                      constraintType = FeatureValuePair.LE;
                  break;
                case ge:
                case lt:
                case gt:
                  switch (jj_nt.kind) {
                  case ge:
                    t = jj_consume_token(ge);
                        constraintType = FeatureValuePair.GE;
                    break;
                  case lt:
                  case gt:
                    switch (jj_nt.kind) {
                    case gt:
                      t = jj_consume_token(gt);
                          constraintType = FeatureValuePair.GT;
                      break;
                    case lt:
                      t = jj_consume_token(lt);
                          constraintType = FeatureValuePair.LT;
                      break;
                    default:
                      jj_la1[9] = jj_gen;
                      jj_consume_token(-1);
                      throw new ParseException();
                    }
                    break;
                  default:
                    jj_la1[10] = jj_gen;
                    jj_consume_token(-1);
                    throw new ParseException();
                  }
                  break;
                default:
                  jj_la1[11] = jj_gen;
                  jj_consume_token(-1);
                  throw new ParseException();
                }
                break;
              default:
                jj_la1[12] = jj_gen;
                jj_consume_token(-1);
                throw new ParseException();
              }
              number = number();
                try {
                  value = new Double(Double.parseDouble(number));
                } catch(NumberFormatException nfe) {
                  {if (true) throw new ParseException("Invalid Number :" + number);}
                }
              break;
            default:
              jj_la1[13] = jj_gen;
              jj_consume_token(-1);
              throw new ParseException();
            }
          }
           aq.add(constraintType, feature, value);
          break;
        default:
          jj_la1[14] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    }
    jj_consume_token(rightbrace);
   {if (true) return aq;}
    throw new Error("Missing return statement in function");
  }

/**
 * UnQuotedText or NamedIndexQuery
 * e.g. microsoft corporation 
 * e.g. root:value
 */
  final public Query TermOrNamedIndexQuery() throws ParseException {
  Token t;
  Token t1;
  String n;
    if (jj_2_6(2)) {
      t = jj_consume_token(tok);
      jj_consume_token(colon);
      t1 = jj_consume_token(tok);
        TermQuery tq = new TermQuery();
        tq.index = unescape(t.image);
        tq.term = unescape(t1.image);
        {if (true) return tq;}
    } else {
      switch (jj_nt.kind) {
      case tok:
        t = jj_consume_token(tok);
        // if no index name is specified, parse the token like a quoted
        // string.
        {if (true) return parseString(t.image);}
        break;
      case number:
      case hyphen:
        // bare number is treated as a query for that number as a string
              n = number();
        {if (true) return parseString(n);}
        break;
      default:
        jj_la1[15] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    throw new Error("Missing return statement in function");
  }

/**
 * obtaining a term or sequence query with all terms in it.
 * e.g. "amazing videos"
 */
  final public Query QuotedTextQuery() throws ParseException {
  String s;
  Token t;
    t = jj_consume_token(string);
    s= t.image.substring(1, t.image.length()-1);
   {if (true) return parseString(s);}
    throw new Error("Missing return statement in function");
  }

/** 
 * OrQuery
 * e.g. Query | Query
 * In the example above, first "Query" is supplied in the previousQuery parameter.
 * This method starts parsing tokens after the previousQuery has ended.
 * i.e. first character after the previous query must be "|"
 */
  final public Query OrQuery(Query previousQuery) throws ParseException {
  ORQuery orQuery = new ORQuery();
  Query q;
  Token t;
    t = jj_consume_token(or);
    if(previousQuery instanceof GapQuery)
      {if (true) throw new ParseException("First element cannot be Gap. See line:"+row+", column:"+column);}

    column = t.beginColumn;
    row = t.beginLine;

    if(previousQuery == null)
      {if (true) throw new ParseException(
         "Use of OR at the start of a query is not permitted. See line:"+row+", column:"+column);}

    orQuery.add(previousQuery);
    q = QueryPlus();
    // merging all ORQueries into one
    // i.e. ORQuery { query1, ORQuery {query2, query3}}
    // will become ORQuery {query1, query2, query3}
    if(q instanceof ORQuery) {
      ORQuery oq = (ORQuery) q;
      for(Query qq : oq.getQueries()) {
        orQuery.add(qq);
      }
    } else {
      orQuery.add(q);
    }
    {if (true) return orQuery;}
    throw new Error("Missing return statement in function");
  }

/** 
 * AndQuery
 * e.g. Query & Query
 * In the example above, first "Query" is supplied in the previousQuery parameter.
 * This method starts parsing tokens after the previousQuery has ended.
 * i.e. first character after the previous query must be "&" Or AND
 */
  final public Query AndQuery(Query previousQuery) throws ParseException {
  ANDQuery andQuery = new ANDQuery();
  Query q;
  Token t;
    t = jj_consume_token(and);
    if(previousQuery instanceof GapQuery)
      {if (true) throw new ParseException("First element cannot be Gap. See line:"+row+", column:"+column);}

    column = t.beginColumn;
    row = t.beginLine;

    if(previousQuery == null)
      {if (true) throw new ParseException(
         "Use of AND at the start of a query is not permitted. See line:"+row+", column:"+column);}

    andQuery.add(previousQuery);
    q = QueryPlus();
    // merging all ANDQueries into one
    // i.e. ANDQuery { query1, ANDQuery {query2, query3}}
    // will become ANDQuery {query1, query2, query3}
    if(q instanceof ANDQuery) {
      ANDQuery aq = (ANDQuery) q;
      for(Query qq : aq.getQueries()) {
        andQuery.add(qq);
      }
    } else {
      andQuery.add(q);
    }
    {if (true) return andQuery;}
    throw new Error("Missing return statement in function");
  }

/** 
 * InQuery
 * e.g. Query IN Query
 * In the example above, first "Query" is supplied in the previousQuery parameter.
 * This method starts parsing tokens after the previousQuery has ended.
 * i.e. first character after the previous query must be "IN"
 */
  final public Query InQuery(Query previousQuery) throws ParseException {
  InQuery inQuery = new InQuery();
  Query q;
  Token t;
    t = jj_consume_token(in);
    if(previousQuery instanceof GapQuery)
      {if (true) throw new ParseException("First element cannot be Gap. See line:"+row+", column:"+column);}

    column = t.beginColumn;
    row = t.beginLine;

    if(previousQuery == null)
      {if (true) throw new ParseException(
         "Use of IN at the start of a query is not permitted. See line:"+row+", column:"+column);}

    inQuery.innerQuery = previousQuery;
    q = QueryPlus();
    inQuery.outerQuery = q;
    {if (true) return inQuery;}
    throw new Error("Missing return statement in function");
  }

/** 
 * OverQuery
 * e.g. Query OVER Query
 * In the example above, first "Query" is supplied in the previousQuery parameter.
 * This method starts parsing tokens after the previousQuery has ended.
 * i.e. first character after the previous query must be "OVER"
 */
  final public Query OverQuery(Query previousQuery) throws ParseException {
  OverQuery overQuery = new OverQuery();
  Query q;
  Token t;
    t = jj_consume_token(over);
    if(previousQuery instanceof GapQuery)
      {if (true) throw new ParseException(
        "First element cannot be Gap. See line:"+row+", column:"+column);}

    column = t.beginColumn;
    row = t.beginLine;

    if(previousQuery == null)
      {if (true) throw new ParseException(
         "Use of Over at the start of a query is not permitted. " +
         "See line:"+row+", column:"+column);}

    overQuery.overQuery = previousQuery;
    q = QueryPlus();
    overQuery.innerQuery = q;
    {if (true) return overQuery;}
    throw new Error("Missing return statement in function");
  }

/** 
 * KleneQuery
 * e.g. Query+3, Query+3..5
 * In all the above examples, "Query" is supplied in the previousQuery parameter.
 * This method starts parsing tokens after the previousQuery has ended.
 * i.e. first character after the previous query must be "+"
 */
  final public Query KleneQuery(Query previousQuery) throws ParseException {
  KleneQuery kq = new KleneQuery();
  Token t;
  Query q;
    t = jj_consume_token(plus);
    if(previousQuery instanceof GapQuery)
      {if (true) throw new ParseException("First element cannot be Gap." +
      " See line:"+row+", column:"+column);}

    column = t.beginColumn;
    row = t.beginLine;

    if(previousQuery == null)
      {if (true) throw new ParseException(
         "Use of KleneOperator + at the start of a query is not permitted." +
         " See line:"+row+", column:"+column);}

    q = previousQuery;
    kq.query = q;
    kq.min = 1;
    kq.max = 1;
    t = jj_consume_token(number);
      kq.max = Integer.parseInt(t.image);
    switch (jj_nt.kind) {
    case period:
      jj_consume_token(period);
      jj_consume_token(period);
      t = jj_consume_token(number);
        kq.min = kq.max;
        kq.max = Integer.parseInt(t.image);
      break;
    default:
      jj_la1[16] = jj_gen;
      ;
    }
   {if (true) return kq;}
    throw new Error("Missing return statement in function");
  }

  final private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_1(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(0, xla); }
  }

  final private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_2(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(1, xla); }
  }

  final private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_3(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(2, xla); }
  }

  final private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_4(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(3, xla); }
  }

  final private boolean jj_2_5(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_5(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(4, xla); }
  }

  final private boolean jj_2_6(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_6(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(5, xla); }
  }

  final private boolean jj_3R_5() {
    if (jj_scan_token(tok)) return true;
    return false;
  }

  final private boolean jj_3_6() {
    if (jj_scan_token(tok)) return true;
    if (jj_scan_token(colon)) return true;
    return false;
  }

  final private boolean jj_3_1() {
    if (jj_3R_3()) return true;
    return false;
  }

  final private boolean jj_3R_3() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(17)) {
    jj_scanpos = xsp;
    if (jj_scan_token(25)) {
    jj_scanpos = xsp;
    if (jj_scan_token(27)) {
    jj_scanpos = xsp;
    if (jj_scan_token(33)) {
    jj_scanpos = xsp;
    if (jj_scan_token(34)) {
    jj_scanpos = xsp;
    if (jj_scan_token(37)) {
    jj_scanpos = xsp;
    if (jj_scan_token(39)) {
    jj_scanpos = xsp;
    if (jj_scan_token(35)) {
    jj_scanpos = xsp;
    if (jj_scan_token(43)) {
    jj_scanpos = xsp;
    if (jj_scan_token(40)) {
    jj_scanpos = xsp;
    if (jj_scan_token(38)) {
    jj_scanpos = xsp;
    if (jj_3R_7()) return true;
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    return false;
  }

  final private boolean jj_3R_4() {
    if (jj_scan_token(string)) return true;
    return false;
  }

  final private boolean jj_3R_6() {
    if (jj_scan_token(period)) return true;
    if (jj_scan_token(regex)) return true;
    return false;
  }

  final private boolean jj_3_5() {
    if (jj_3R_6()) return true;
    return false;
  }

  final private boolean jj_3R_7() {
    if (jj_scan_token(number)) return true;
    return false;
  }

  final private boolean jj_3_3() {
    if (jj_scan_token(and)) return true;
    return false;
  }

  final private boolean jj_3_4() {
    if (jj_scan_token(equals)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_4()) {
    jj_scanpos = xsp;
    if (jj_3R_5()) return true;
    }
    return false;
  }

  final private boolean jj_3_2() {
    if (jj_scan_token(or)) return true;
    return false;
  }

  public QueryParserTokenManager token_source;
  SimpleCharStream jj_input_stream;
  public Token token, jj_nt;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  public boolean lookingAhead = false;
  private boolean jj_semLA;
  private int jj_gen;
  final private int[] jj_la1 = new int[17];
  static private int[] jj_la1_0;
  static private int[] jj_la1_1;
  static {
      jj_la1_0();
      jj_la1_1();
   }
   private static void jj_la1_0() {
      jj_la1_0 = new int[] {0xa060000,0xa060000,0x0,0x20000000,0x0,0x20000000,0x0,0x0,0x20000,0x1800000,0x1c00000,0x1e00000,0x41e00000,0x41e00000,0x41e00000,0x40000,0x20000000,};
   }
   private static void jj_la1_1() {
      jj_la1_1 = new int[] {0x9ee,0x9e0,0x8,0x0,0x40,0x0,0x1,0x800,0x800,0x0,0x0,0x0,0x0,0x0,0x0,0x840,0x0,};
   }
  final private JJCalls[] jj_2_rtns = new JJCalls[6];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  public QueryParser(java.io.InputStream stream) {
     this(stream, null);
  }
  public QueryParser(java.io.InputStream stream, String encoding) {
    try { jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new QueryParserTokenManager(jj_input_stream);
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public QueryParser(java.io.Reader stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new QueryParserTokenManager(jj_input_stream);
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public QueryParser(QueryParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(QueryParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    token.next = jj_nt = token_source.getNextToken();
    jj_gen = 0;
    for (int i = 0; i < 17; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken = token;
    if ((token = jj_nt).next != null) jj_nt = jj_nt.next;
    else jj_nt = jj_nt.next = token_source.getNextToken();
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    jj_nt = token;
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static private final class LookaheadSuccess extends java.lang.Error { }
  final private LookaheadSuccess jj_ls = new LookaheadSuccess();
  final private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    if (jj_scanpos.kind != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) throw jj_ls;
    return false;
  }

  final public Token getNextToken() {
    if ((token = jj_nt).next != null) jj_nt = jj_nt.next;
    else jj_nt = jj_nt.next = token_source.getNextToken();
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = lookingAhead ? jj_scanpos : token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      boolean exists = false;
      for (java.util.Enumeration e = jj_expentries.elements(); e.hasMoreElements();) {
        int[] oldentry = (int[])(e.nextElement());
        if (oldentry.length == jj_expentry.length) {
          exists = true;
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              exists = false;
              break;
            }
          }
          if (exists) break;
        }
      }
      if (!exists) jj_expentries.addElement(jj_expentry);
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[44];
    for (int i = 0; i < 44; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 17; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 44; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

  final private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 6; i++) {
    try {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
            case 4: jj_3_5(); break;
            case 5: jj_3_6(); break;
          }
        }
        p = p.next;
      } while (p != null);
      } catch(LookaheadSuccess ls) { }
    }
    jj_rescan = false;
  }

  final private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
