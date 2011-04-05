/*
 *  Copyright (c) 1998-2011, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
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
