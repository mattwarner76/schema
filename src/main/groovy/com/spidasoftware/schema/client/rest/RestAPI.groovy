package com.spidasoftware.schema.client.rest

import com.spidasoftware.schema.client.HttpClientInterface
import groovy.util.logging.Log4j
import net.sf.json.JSON
import net.sf.json.JSONObject
import net.sf.json.JSONSerializer
import org.apache.http.util.EntityUtils

/**
 * Use for making calls to a rest api. Defaults to a JSON-based api, but can be easily configured to use xml instead.
 * Most methods in this class should not be called directly, but instead by a child RestAPIResource.
 *
 * The only thing to be done with the api directly is setting it's default configuration that get's used by all of it's
 * child resources. You can access this property directly as <code>api.defaults</code>. You can also externalize
 * the configuration and pass in a directory containing the .config files to use.
 *
 * Created with IntelliJ IDEA.
 * User: pfried
 * Date: 1/15/14
 * Time: 7:35 PM
 * To change this template use File | Settings | File Templates.
 */
@Log4j
class RestAPI {

	/**
	 * The client to use for making http calls.
	 */
	HttpClientInterface client

	/**
	 * The base URI to use for all of the api calls. i.e.- "http://www.website.com"
	 */
    String baseUrl

	/**
	 * The list of resources associated with this RestAPI. Each resource stores it's own settings, which can override the defaults.
	 */
    List<RestAPIResource> resources = []

	/**
	 * Default configurations to use for all api calls. Individual resources will inherit from these settings.
	 * More specifically, each resources settings will be merged into a copy of <code>defaults</code> before each api call.
	 * Thus allowing only portions of the default config to be overridden.
	 */
	ConfigObject defaults
	File configDirectory    // Location for external configuration files, may be null

	RestAPI(String baseUrl, HttpClientInterface client) {
        this(baseUrl, client, null)

    }

	RestAPI(String baseUrl, HttpClientInterface client, File configDirectory) {
		this.baseUrl = baseUrl
		this.client = client
		this.configDirectory = configDirectory
		loadDefaults()
	}

	void loadDefaults(){
		this.defaults = getDefaultConfigFromResources()
		if (this.configDirectory) {
			log.debug("Loading configuration for RestAPI from: ${this.configDirectory.getCanonicalPath()}")
			File defaultConfigFile = new File(this.configDirectory, "defaults.config")
			if (defaultConfigFile.exists()) {
				try {

					def config = new ConfigSlurper().parse(defaultConfigFile.toURI().toURL())
					log.info("RestAPI defaults have been overridden by ${defaultConfigFile.getCanonicalPath()}")
					this.defaults.merge(config)
					return

				} catch (Exception e) {
					log.error("Error loading ${defaultConfigFile.getCanonicalPath()}. Falling back to built-in defaults", e)
				}
			} else {
				log.warn("RestAPI config directory was specified, but no config file was found! Using built-in defaults instead.")
			}
		}
	}



	protected ConfigObject getDefaultConfigFromResources(){
		log.info("Using default configuration for RestAPI")

		URL configUrl = getClass().getResource("/client/rest/defaults.config")
		def config = new ConfigSlurper().parse(configUrl)
		return config
	}

	def find(ConfigObject settings, String id) {
		def config = mergeConfig(settings)

		URI uri = createURI(config.path, id)
		Map headers = config.headers
		def result = client.executeRequest("GET", uri, config.additionalParams, headers, config.doWithResponse)
		return config.doWithFindResult.call(result)
	}

	def list(ConfigObject settings, Map params) {
		def config = mergeConfig(settings)
		URI uri = createURI(config.path)
		Map headers = config.headers
		def result = client.executeRequest("GET", uri, mergeParams(params, config.additionalParams), headers, config.doWithResponse)
		return config.doWithListResult.call(result)
	}

	def update(ConfigObject settings, Map params, String id) {
		def config = mergeConfig(settings)
		URI uri = createURI(config.path, id)
		Map headers = config.headers
		def result = client.executeRequest("PUT", uri, mergeParams(params, config.additionalParams), headers, config.doWithResponse)

		return config.doWithUpdateResult.call(result)
	}

	def save(ConfigObject settings, Map params) {
		def config = mergeConfig(settings)
		URI uri = createURI(config.path)
		Map headers = config.headers

		def result = client.executeRequest("POST", uri, mergeParams(params, config.additionalParams), headers, config.doWithResponse)

		return config.doWithSaveResult.call(result)
	}

	def delete(ConfigObject settings, String id) {
		def config = mergeConfig(settings)
		URI uri = createURI(config.path, id)
		Map headers = config.headers
		def result = client.executeRequest("DELETE", uri, config.additionalParams, headers, config.doWithResponse)

		return config.doWithDeleteResult.call(result)

	}

	URI createURI(String path, String id = null) {
		StringBuilder sb = new StringBuilder()
		String base
		if (baseUrl.endsWith('/')) {
			sb.append(baseUrl.substring(0, baseUrl.lastIndexOf('/')))
		} else {
			sb.append(baseUrl)
		}
		if (path && path != "/") {
			sb.append(path.startsWith('/')? path : "/"+ path)
		}
		if (id) {
			sb.append("/" + id)
		}

		return new URI(sb.toString())

	}


	protected static Map mergeParams(Map originalParams, Map additional) {
		if (additional && !additional.isEmpty()) {
			return additional.plus(originalParams)
		}
		return originalParams
	}

	def getResultAsJSON = { response ->
		def sc = response.getStatusLine().getStatusCode()
		JSON json

		try {
			json = JSONSerializer.toJSON(EntityUtils.toString(response.getEntity()))
		} catch (Exception e) {
			throw new RestClientException("Response was not valid JSON", e)
		}
		JSONObject j = new JSONObject()
		j.put("status", sc)
		j.put("json", json)
		return j

	}


	ConfigObject mergeConfig(ConfigObject resourceSettings) {
		def d = this.defaults.clone()

		d.merge(resourceSettings)
		return d
	}

	def propertyMissing(String name) {
		def res = this.resources.find{ it.name == name }
		if (!res) {
			res = new RestAPIResource(name, this)
			this.resources.add(res)
		}

		return res
	}

	def propertyMissing(String name, value) {
		if (value instanceof RestAPIResource) {
			def res = this.resources.find {it.name == name }
			if (res) { this.resources.remove(res) }
			this.resources.add(res)
			return res
		} else {
			throw new NoSuchFieldException("No such field: $name for class RestAPI")
		}

	}
}