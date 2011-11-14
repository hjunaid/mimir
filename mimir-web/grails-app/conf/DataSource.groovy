/*
 *  DataSource.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */

dataSource {
	pooled = true
	driverClassName = "org.hsqldb.jdbcDriver"
	username = "sa"
	password = ""
}
hibernate {
    cache.use_second_level_cache=true
    cache.use_query_cache=true
    cache.provider_class='com.opensymphony.oscache.hibernate.OSCacheProvider'
}
// environment specific settings
environments {
	development {
		dataSource {
			dbCreate = "update" // one of 'create', 'create-drop','update'
			url = "jdbc:hsqldb:file:devDb;shutdown=true"
		}
	}
	test {
		dataSource {
			dbCreate = "update"
			url = "jdbc:hsqldb:mem:testDb"
		}
	}
	production {
		dataSource {
			dbCreate = "update"
			url = "jdbc:hsqldb:file:prodDb;shutdown=true"
		}
	}
}