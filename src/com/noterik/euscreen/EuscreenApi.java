package com.noterik.euscreen;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.json.XML;
import org.json.JSONArray;
import org.json.JSONException;

import org.springfield.fs.FSList;
import org.springfield.fs.FSListManager;
import org.springfield.fs.FsNode;

public class EuscreenApi extends HttpServlet {

	private static String DEFAULT_QUERY = "";
	private static int MAX_RESULTS = 20;
	private static String[] DEFAULT_TYPES = {"video", "picture"};
	private static int JSON_IDENTATION = 4;
	private static final long serialVersionUID = 1L;
	private FSList allNodes;
	

	public EuscreenApi() {
        super();
        
        System.out.println("EuscreenApi");
    }
    
    /**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//	First get the non filter parameters
		String query = request.getParameter("query") == null ? DEFAULT_QUERY : request.getParameter("query");
		int maxResults = request.getParameter("limit") == null ? MAX_RESULTS : Integer.parseInt(request.getParameter("limit"));
		String[] supportedTypes = request.getParameter("types") == null ? DEFAULT_TYPES : request.getParameter("types").split(",");
		
		// Everything else is being applied as a filter
		Map<String, String[]> includeFilters = new HashMap<>(request.getParameterMap());
		includeFilters.remove("query");
		includeFilters.remove("limit");
		includeFilters.remove("types");
		// If not specified, search only for public items
		if (!includeFilters.containsKey("public")) {
			includeFilters.put("public", new String[] {"true"});
		}
		
		System.out.println("EUscreen api: load nodes");
		
		String uri = "";
		Map<String, String[]> excludeFilters = new HashMap<String, String[]>();
		
		// If not specified search entire dataset, excluding agency
		if (!includeFilters.containsKey("collection")) {
			uri = "/domain/euscreenxl/user/*/*";
			allNodes = FSListManager.get(uri);
			
			excludeFilters.put("provider", new String[] {"AGENCY"});
		} else {
			// Otherwise assume it's a agency search 
			String collectionName = String.join("", includeFilters.get("collection"));
			uri = "/domain/euscreenxl/user/eu_agency/collection/"+collectionName+"/teaser";
			
			allNodes = FSListManager.get(uri, false);
			
			includeFilters.remove("collection");
		}

		System.out.println("EUscreen api: nodes loaded (size = "+allNodes.size()+")");
		
		
		if (allNodes.size() > 0) {
			List<FsNode> results = allNodes.getNodesFiltered(query);
			
			results = applyFilters(results, includeFilters, excludeFilters);
				
			if (results.size() > 1) {
				JSONArray jsonArray = new JSONArray();
				int passedResults = 0;
							
				for (FsNode result : results) {
					// Don't return more then the max results
					if (passedResults < maxResults) {
						// Filter on types
						if (supportedType(result, supportedTypes)) {
							passedResults++;
							try {
					            JSONObject xmlJSONObj = XML.toJSONObject(result.asXML());
					            jsonArray.put(xmlJSONObj);
							} catch (JSONException je) {
					            System.out.println(je.toString());
					            response.setStatus(500);
					            writeResponseJSON(response, getError(500));
					        }
						}
					} else {
						break;
					}
				}				
				writeResponseJSON(response, jsonArray);				
			} else {
				JSONObject json = new JSONObject();
				 writeResponseJSON(response, json);
			}
		} else {
			System.out.println("No nodes loaded");
            response.setStatus(500);
            writeResponseJSON(response, getError(500));
		}
	}
	
	private boolean supportedType(FsNode node, String[] types) {
		for (String type : types) {
			if (node.getName().equals(type)) {
				return true;
			}
		}
		return false;
	}
	
	private JSONObject getError(int errorCode) {
		JSONObject json = new JSONObject();
		json.put("error", errorCode);
		return json;
	}
	
	private void writeResponseJSON(HttpServletResponse response, JSONArray json) throws IOException {
		response.setContentType("application/json");
		
		String jsonPrettyPrintString = json.toString(JSON_IDENTATION);
        PrintWriter writer = response.getWriter();
        writer.write(jsonPrettyPrintString);
        writer.close();
	}
	
	private void writeResponseJSON(HttpServletResponse response, JSONObject json) throws IOException {
		response.setContentType("application/json");
		
		String jsonPrettyPrintString = json.toString(JSON_IDENTATION);
        PrintWriter writer = response.getWriter();
        writer.write(jsonPrettyPrintString);
        writer.close();
	}
	
	private List<FsNode> applyFilters(List<FsNode> results, Map<String, String[]> includeFilters, Map<String, String[]> excludeFilters) {
		List<FsNode> filteredRresults = new ArrayList<FsNode>();
		
		boolean mismatch = false;
		
		// Loop over all results
		for(Iterator<FsNode> iterator = results.iterator() ; iterator.hasNext(); ) {
			FsNode fsNode = (FsNode) iterator.next();	
			
			// Check if all includeFilters are matched, if not mismatch is set to true
			for (Map.Entry<String, String[]> filter : includeFilters.entrySet()) {
				for (String searchKey : filter.getValue()) {
					if (fsNode.getProperty(filter.getKey()) == null || fsNode.getProperty(filter.getKey()).indexOf(searchKey) == -1) {
						mismatch = true;
						break;
					}
				}				
			}
			
			// Check if all excludeFilters are not matched, if matching mismatch is set to true
			for (Map.Entry<String, String[]> filter : excludeFilters.entrySet()) {
				for (String searchKey : filter.getValue()) {
					if (fsNode.getProperty(filter.getKey()) != null && fsNode.getProperty(filter.getKey()).indexOf(searchKey) != -1) {
						mismatch = true;
						break;
					}
				}				
			}
			
			// Only when all filters passed (no mismatch occurred) we add the result to our filteredResults
			if (!mismatch) {
				filteredRresults.add(fsNode);
			}
			mismatch = false;

		}
		return filteredRresults;
	}
} 
