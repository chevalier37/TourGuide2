package com.tourguide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.tourguide.helper.InternalTestHelper;
import com.tourguide.tracker.Tracker;
import com.tourguide.user.User;
import com.tourguide.user.UserPreferences;
import com.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	

	
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		if(testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		
		tracker = new Tracker(this);
		
		addShutDownHook();
	}
	
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}
	

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}
	
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}
	
	public void addUser(User user) {
		if(!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}
	
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(), user.getUserPreferences().getNumberOfAdults(), 
				user.getUserPreferences().getNumberOfChildren(), user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}
	
	@Async("asyncExecutor")
	public CompletableFuture<VisitedLocation> getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ?
			user.getLastVisitedLocation() :
			trackUserLocation(user);
		return CompletableFuture.completedFuture(visitedLocation);
	}
	
	@Async("asyncExecutor")
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		VisitedLocation visitedLocation = getUserLocation(getUser(user.getUserName()));
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return CompletableFuture.completedFuture(visitedLocation);
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for(Attraction attraction : gpsUtil.getAttractions()) {
			if(rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}
		
		return nearbyAttractions;
	}
	
	//created by JB
	public TreeMap<Double,Attraction> get5NearByAttractions(VisitedLocation visitedLocation) {
		TreeMap<Double,Attraction> nearbyAttractions = new TreeMap<Double,Attraction>();
		TreeMap<Double,Attraction> orderedNearbyAttractions = new TreeMap<Double,Attraction>();
		for(Attraction attraction : gpsUtil.getAttractions()) {
			double distance = rewardsService.getDistanceMiles(attraction, visitedLocation.location);
			nearbyAttractions.put(distance,attraction);
			nearbyAttractions.keySet();
		}
		int i = 0;
		for(Map.Entry<Double,Attraction> entry : nearbyAttractions.entrySet()) {
			orderedNearbyAttractions.put(entry.getKey(), entry.getValue());
			i++;
				if(i==5) {
					break;
				}
			}
		return orderedNearbyAttractions;
	}
	
	
	
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() { 
		      public void run() {
		        tracker.stopTracking();
		      } 
		    }); 
	}
	
	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();
	

	private void initializeInternalUsers() {
		
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			 //created by JB
			 int nbrAdult = ThreadLocalRandom.current().nextInt(1, 4);
			 int nbrChild = ThreadLocalRandom.current().nextInt(1, 4);
			 int nbrNight = ThreadLocalRandom.current().nextInt(1, 15);
			 int nbrTicket = ThreadLocalRandom.current().nextInt(1, 4);
			
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			 //created by JB
			UserPreferences userPreferences = new UserPreferences(nbrNight, nbrTicket, nbrAdult, nbrChild);
			User user = new User(UUID.randomUUID(), userName, phone, email, userPreferences);
			generateUserLocationHistory(user);
			
			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}
	
	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i-> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}
	
	private double generateRandomLongitude() {
		double leftLimit = -180;
	    double rightLimit = 180;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
	    double rightLimit = 85.05112878;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
	    return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
	
}
