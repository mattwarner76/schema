package com.spidasoftware.schema.changesets

import net.sf.json.JSONObject

/**
 * Created by jeremy on 4/16/14.
 */
class JsonLibTests extends GroovyTestCase {

	void testNetSfJsonNull(){
		def json = new JSONObject()
		json.a = "1"
		assert json.containsKey("a")

		json.a = null
		assert !json.containsKey("a") // opposite of map

		json.put("a", null)
		assert !json.containsKey("a") // opposite of map
	}

	void testMapNull(){
		def json = [:]
		json.a = "1"
		assert json.containsKey("a")

		json.a = null
		assert json.containsKey("a") // opposite net.sf.json

		json.put("a", null)
		assert json.containsKey("a") // opposite net.sf.json
	}
}
