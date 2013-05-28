package ch.specchio.services;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import au.ands.org.researchdata.ResearchDataAustralia;
import au.ands.org.researchdata.SpectralLibrary;

import ch.specchio.constants.UserRoles;
import ch.specchio.factories.SPECCHIOFactoryException;
import ch.specchio.factories.UserFactory;
import ch.specchio.jaxb.XmlInteger;
import ch.specchio.types.Capabilities;
import ch.specchio.types.Institute;
import ch.specchio.types.User;


/**
 * Services accessible without a user account.
 */
@Path("/public")
public class PublicService extends SPECCHIOService {
	
	
	/**
	 * Get the capabilities of the server.
	 * 
	 * @return a Capabilities object
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	@GET
	@Produces(MediaType.APPLICATION_XML)
	@Path("capabilities")
	public Capabilities capabilities() throws SPECCHIOFactoryException {
		
		return getServerCapabilities();
		
	}
	
	
	/**
	 * Create a new user account.
	 * 
	 * @param user	an object describing the user to be created
	 *
	 * @throws SecurityException		a non-admin user tried to create an admin user
	 * @throws SPECCHIOFactoryException	database error
	 * 
	 * @return a user object containing the complete details of the account
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	@Path("createUserAccount")
	public User createUserAccount(User user) throws SPECCHIOFactoryException {
		
		if (user.isInRole(UserRoles.ADMIN)) {
			// an anonymous user is trying to create an admin user
			throw new SecurityException("Administrator accounts cannot be created using this service.");
		}
		
		// create a user factory to do the work
		UserFactory factory = new UserFactory();
		
		// create the account
		factory.insertUser(user);
		
		if (getServerCapability(ResearchDataAustralia.ANDS_SERVER_CAPABILITY) != null) {
			// make sure the user has an ANDS party identifier
			if (user.getExternalId() == null || user.getExternalId().length() == 0) {
				user.setExternalId(ResearchDataAustralia.generatePartyIdentifier(SpectralLibrary.PARTY_ID_PREFIX, user));
				factory.updateUser(user);
			}
		}
		
		// clean up
		factory.dispose();
		
		return user;
		
	}
	
	
	/**
	 * Insert a new institute into the database.
	 * 
	 * @param institute	the new institute
	 * 
	 * @return the identifier of the new institute
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	@Path("insertInstitute")
	public XmlInteger insertInstitute(Institute institute) throws SPECCHIOFactoryException {
		
		UserFactory factory = new UserFactory();
		int institute_id = factory.insertInstitute(institute);
		factory.dispose();
		
		return new XmlInteger(institute_id);
		
	}
	
	
	/**
	 * Get a list of all of the institutes in the database.
	 * 
	 * @return an array of Institute objects representing every known institute
	 * 
	 * @throws SPECCHIOFactoryException	could not connect to the database
	 */
	@GET
	@Produces(MediaType.APPLICATION_XML)
	@Path("listInstitutes")
	public Institute[] listInstitutes() throws SPECCHIOFactoryException {
		
		UserFactory factory = new UserFactory();
		Institute institutes[] = factory.getInstitutes();
		factory.dispose();
		
		return institutes;
		
	}

}