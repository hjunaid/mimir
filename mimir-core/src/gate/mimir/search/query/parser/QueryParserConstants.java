/*
 *  QueryParserConstants.java
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
 *  $Id$
 */
package gate.mimir.search.query.parser;

public interface QueryParserConstants {

  int EOF = 0;
  int string = 17;
  int number = 18;
  int escape = 19;
  int special = 20;
  int le = 21;
  int ge = 22;
  int lt = 23;
  int gt = 24;
  int leftbrace = 25;
  int rightbrace = 26;
  int leftbracket = 27;
  int rightbracket = 28;
  int period = 29;
  int equals = 30;
  int colon = 31;
  int comma = 32;
  int or = 33;
  int and = 34;
  int plus = 35;
  int question = 36;
  int in = 37;
  int hyphen = 38;
  int over = 39;
  int leftsquarebracket = 40;
  int rightsquarebracket = 41;
  int regex = 42;
  int tok = 43;

  int DEFAULT = 0;
  int IN_STRING = 1;

  String[] tokenImage = {
    "<EOF>",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "\"\\t\"",
    "\" \"",
    "\"\\\"\"",
    "\"\\\\n\"",
    "\"\\\\r\"",
    "\"\\\\t\"",
    "\"\\\\b\"",
    "\"\\\\f\"",
    "\"\\\\\\\"\"",
    "\"\\\\\\\'\"",
    "\"\\\\\\\\\"",
    "<token of kind 15>",
    "<token of kind 16>",
    "\"\\\"\"",
    "<number>",
    "<escape>",
    "<special>",
    "\"<=\"",
    "\">=\"",
    "\"<\"",
    "\">\"",
    "\"{\"",
    "\"}\"",
    "\"(\"",
    "\")\"",
    "\".\"",
    "\"=\"",
    "\":\"",
    "\",\"",
    "<or>",
    "<and>",
    "\"+\"",
    "\"?\"",
    "\"IN\"",
    "\"-\"",
    "\"OVER\"",
    "\"[\"",
    "\"]\"",
    "\"REGEX\"",
    "<tok>",
  };

}
