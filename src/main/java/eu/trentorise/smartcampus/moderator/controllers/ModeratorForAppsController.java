package eu.trentorise.smartcampus.moderator.controllers;

import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Order;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.trentorise.smartcampus.aac.AACException;
import eu.trentorise.smartcampus.aac.AACService;
import eu.trentorise.smartcampus.moderator.model.ModeratorForApps;
import eu.trentorise.smartcampus.moderator.utils.EasyTokenManger;
import eu.trentorise.smartcampus.profileservice.BasicProfileService;
import eu.trentorise.smartcampus.profileservice.ProfileServiceException;
import eu.trentorise.smartcampus.profileservice.model.BasicProfile;
import eu.trentorise.smartcampus.resourceprovider.controller.SCController;
import eu.trentorise.smartcampus.resourceprovider.model.AuthServices;
import eu.trentorise.smartcampus.resourceprovider.model.ResourceParameter;


@Controller
public class ModeratorForAppsController extends SCController {
	
	private static final String MODERATOR_SERVICE_ID = "smartcampus.moderation";
	private static final String MODERATOR_RESOURCE_PARAMETER_ID = "app";
	
	@Autowired
	MongoTemplate db;
	
	@Autowired
	@Value("${aacExtURL}")
	private String aacExtURL;
	

	@Autowired
	@Value("${aacURL}")
	private String aacURL;

	@Autowired
	@Value("${smartcampus.clientId}")
	private String client_id;

	@Autowired
	@Value("${smartcampus.clientSecret}")
	private String client_secret;

	@Autowired
	private AuthServices services;
	
	protected AACService aacService;
	protected BasicProfileService profileService;

	@PostConstruct
	public void init() {
		aacService = new AACService(aacExtURL, client_id, client_secret);
		profileService = new BasicProfileService(aacURL);
	}
	
	@Override
	protected AuthServices getAuthServices() {
		return services;
	}
	
	protected String getToken(HttpServletRequest request) {

		// String fromCtx = (String)
		// SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String fromCtx = (String) request.getSession().getAttribute("token");

		System.err.println("TOKEN: " + fromCtx);
		return fromCtx;
	}
	
	@RequestMapping(method = RequestMethod.POST, value = "/rest/moderator/app/{app}/add")
	public @ResponseBody
	String addModerator(HttpServletRequest request,@PathVariable String app,@RequestBody List<HashMap> userIds) throws ProfileServiceException, AACException {
		
		///clean after test
		BasicProfile ownerProfile=profileService.getBasicProfile("99440746-db99-4951-b58d-bada7324753d");
		
		//load profiles like client core.moderator	
		long startTime=System.currentTimeMillis();
		long endtime=startTime + Long.valueOf("2628000000");	
		
		
		String token=new EasyTokenManger(aacURL, client_id, client_secret).getClientSmartCampusToken();
		
				
		String clientIdOfApp="";
		
		List<ResourceParameter> lstResourceParameters=services.loadResourceParameterByUserId(ownerProfile.getUserId());
		for(ResourceParameter rp : lstResourceParameters){
			if (!MODERATOR_SERVICE_ID.equals(rp.getServiceId()) && !MODERATOR_RESOURCE_PARAMETER_ID.equals(rp.getResourceId())) continue;
			if(rp.getValue().compareTo(app)==0){
				clientIdOfApp=rp.getClientId();
				break;
			}			
		}
				
			
		
		ModeratorForApps newModeratorIstance=null;
		
		for(int indexString = 0;indexString<userIds.size();indexString++){
			String userId=String.valueOf(userIds.get(indexString).get("userId"));	
			startTime=(Long) userIds.get(indexString).get("startTime");	
			endtime=(Long) userIds.get(indexString).get("endTime");	
			
			BasicProfile index=profileService.getBasicProfile(userId, token);
			Query insert=new Query();
			insert.addCriteria(Criteria.where("userId").regex(index.getUserId()));
			insert.addCriteria(Criteria.where("appId").regex(app));
			newModeratorIstance=db.findOne(insert, ModeratorForApps.class);
			if(newModeratorIstance==null){			
				newModeratorIstance=new ModeratorForApps(index,app,ownerProfile.getUserId(),startTime,endtime,clientIdOfApp);
			}else{
				db.remove(newModeratorIstance);
				//if was moderator for this app,update the parent and the time
				newModeratorIstance.setParentUserId(ownerProfile.getUserId());
				newModeratorIstance.setStartTime(startTime);
				newModeratorIstance.setEndTime(endtime);
			}
			
			db.save(newModeratorIstance);
			
		}		
		
		return "true";
		
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/rest/moderator/app/{app}/all")
	public @ResponseBody
	List<ModeratorForApps> getAllModerator(HttpServletRequest request,@PathVariable String app) throws SecurityException{

		Query query2 = new Query();
		query2.sort().on("endTime", Order.DESCENDING);
		query2.addCriteria(Criteria.where("appId").regex(app));

		return db.find(query2, ModeratorForApps.class);
		
	}
	
	@RequestMapping(method = RequestMethod.PUT, value = "/rest/moderator/app/{app}")
	public @ResponseBody
	ModeratorForApps updateModerator(HttpServletRequest request,@PathVariable String app,@RequestBody ModeratorForApps moderatorToUpdate) throws SecurityException{

		Query insert=new Query();
		insert.addCriteria(Criteria.where("userId").regex(moderatorToUpdate.getUserId()));
		insert.addCriteria(Criteria.where("appId").regex(moderatorToUpdate.getAppId()));
		ModeratorForApps newModeratorIstance=db.findOne(insert, ModeratorForApps.class);
		if(newModeratorIstance==null){			
			return null;
		}else{
			db.remove(newModeratorIstance);			
		}
		
		db.save(moderatorToUpdate);
		
		return db.findOne(insert, ModeratorForApps.class);
		
		
	}

	@RequestMapping(method = RequestMethod.DELETE, value = "/rest/moderator/app/{app}/{id}")
	public @ResponseBody
	String deleteModerator(HttpServletRequest request,@PathVariable String app,@PathVariable String id) throws SecurityException{

		
		db.remove(db.findById(id, ModeratorForApps.class));
	
		return "true";
		
		
	}


}
