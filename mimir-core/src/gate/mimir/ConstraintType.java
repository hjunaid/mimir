/*
 *  ConstraintType.java
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
 *  Valentin Tablan, 5 Aug 2009
 *
 *  $Id$
 */
package gate.mimir;

/**
 * Types of predicates used for annotation queries.
 */
public enum ConstraintType
{
    /**
     * Equals predicate.
     */
    EQ,
    
    /**
     * Greater or equal predicate.
     */
    GE,
    
    /**
     * Greater than predicate.
     */
    GT,
    
    /**
     * Less or equal predicate.
     */
    LE,
    
    /**
     * Less than predicate.
     */
    LT,
    
    /**
     * Predicate for regular expression matching (see 
     * {@link http://www.w3.org/TR/rdf-sparql-query/#funcex-regex}).
     * Provide the regular expression pattern as the value of the constraint.
     * If the use of flags is required, provide as the value of the constraint 
     * an array of two strings, the first being the pattern, the second 
     * representing the flags.
     */
    REGEX
}
