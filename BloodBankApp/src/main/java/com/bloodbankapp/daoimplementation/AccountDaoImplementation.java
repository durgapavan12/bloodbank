package com.bloodbankapp.daoimplementation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jongo.Aggregate.ResultsIterator;
import org.jongo.Jongo;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Repository;

import com.bloodbankapp.constants.DB_Constants;
import com.bloodbankapp.constants.ResponseConstants;
import com.bloodbankapp.dao.AccountDao;
import com.bloodbankapp.exception.BloodBankException;
import com.bloodbankapp.pojos.BloodGroup;
import com.bloodbankapp.pojos.Counter;
import com.bloodbankapp.pojos.Login;
import com.bloodbankapp.pojos.Registration;
import com.bloodbankapp.pojos.Response;
import com.bloodbankapp.pojos.Role;
import com.bloodbankapp.pojos.Transaction;
import com.bloodbankapp.pojos.UserPermissions;

import ch.qos.logback.classic.Logger;

@Repository("accountDao")
public class AccountDaoImplementation implements AccountDao {

	@Override
	public Response registration(Registration registration) {

		Response response = new Response();
		Registration registeredUser = new Jongo(DB_Constants.getMongodbDatabase())
				.getCollection(DB_Constants.getRegistrationCol()).findOne("{phNo:#}", registration.getPhNo())
				.as(Registration.class);

		if (registeredUser != null) {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("User already Exist");

		} else {
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getCounterCol()).update("{ _id:1 }")
					.with("{$inc:{seq:1}}");
			Counter counter = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getCounterCol())
					.findOne("{ _id: 1}").as(Counter.class);

			registration.setId(counter.getSeq());
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
					.insert(registration);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("New User Created!");
		}
		return response;
	}

	@Override
	public Login loginCheck(Login login) {

		Response response = new Response();
		Login user = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
				.findOne("{phNo:#}", login.getPhNo()).as(Login.class);
		if (user != null) {
			String generatedlogedPasswordHash = BCrypt.hashpw(login.getPassword(), BCrypt.gensalt(12));
			boolean b = BCrypt.checkpw(user.getPassword(), generatedlogedPasswordHash);
			if (b) {
				response.setStatusCode(ResponseConstants.Success_code);
				response.setStatusMessage("Successfully logged in!");
			} else {
				response.setStatusCode(ResponseConstants.Error_code);
				response.setStatusMessage("Error while logging ");
			}
		} else {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("User Not found");
		}
		return user;
	}

	@Override
	public Response insertBGData(BloodGroup bloodGroup) {

		Response response = new Response();
		try {
			BloodGroup bg = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
					.findOne("{bloodGroup:#}", bloodGroup.getBloodGroup()).as(BloodGroup.class);

			if (bg != null) {
				int n = bg.getQuantity() + bloodGroup.getQuantity();
				bg.setQuantity(n);
				int flag = 0;
				if (bloodGroup.getAmount() != bg.getAmount()&&bloodGroup.getAmount()>0) {
					bg.setAmount(bloodGroup.getAmount());
					response.setStatusMessage("amount changed!");
					if (bloodGroup.getQuantity()>0) {
						response.setStatusMessage("amount changed and quantity increased!");
					}
					flag = 1;
				}
				new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
						.update("{bloodGroup:#}", bloodGroup.getBloodGroup()).upsert().with(bg);
				response.setStatusCode(ResponseConstants.Success_code);
				if (flag == 0)
					response.setStatusMessage("Successfully blood group qty added!");
			} else {
				new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
						.insert(bloodGroup);
				response.setStatusCode(ResponseConstants.Success_code);
				response.setStatusMessage("Successfully inserted new bloodgroup!");
			}

		} catch (Exception e) {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("Error while inserting bloodgroup details");
		}

		return response;
	}

	@Override
	public Response insertTransaction(Transaction transaction) {
		Response response = new Response();
		try {

			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getCounterCol())
					.update("{ _id:'transactionID' }").with("{$inc:{seq:1}}");
			Counter counter = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getCounterCol())
					.findOne("{ _id: 'transactionID'}").as(Counter.class);

			transaction.setT_id(counter.getSeq());

			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getTransactionCol())
					.insert(transaction);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("Amount transfered!");
		}

		catch (Exception e) {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("Error while inserting amount");
		}
		return response;

	}

	@Override
	public List<Transaction> fetchTransaction() {

		List<Transaction> list = new ArrayList<>();

		Iterator iterator = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getTransactionCol())
				.find().as(Transaction.class).iterator();
		while (iterator.hasNext()) {
			list.add((Transaction) iterator.next());
		}
		return list;
	}

	@Override
	public Response bloodChecking(BloodGroup bloodGroup) {
		Response response = new Response();
		try {
			BloodGroup bg = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
					.findOne("{bloodGroup:#}", bloodGroup.getBloodGroup()).as(BloodGroup.class);
			if (bg != null) {
				if (bg.getQuantity() > bloodGroup.getQuantity()) {
					response.setStatusCode(ResponseConstants.Success_code);
					response.setStatusMessage("Blood Group availble and cost around "+bg.getAmount()*bloodGroup.getQuantity()+"/- for your required quantity");
				} else {
					response.setStatusCode(ResponseConstants.Success_code);
					response.setStatusMessage("Only " + bg.getQuantity() + " units is available and costs around "+bg.getQuantity()*bg.getAmount()+"/-for available quantity ");
				}
			} else {
				response.setStatusCode(ResponseConstants.Error_code);
				response.setStatusMessage("Failed to fetch blood details as blood group is not available");
			}
		} catch (Exception e) {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("Error while checking blood details");
		}

		return response;
	}

	@Override
	public ArrayList<BloodGroup> bloodAvailableDetails() {

		ArrayList<BloodGroup> list = new ArrayList<BloodGroup>();

		Iterator iterator = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
				.find().as(BloodGroup.class).iterator();

		while (iterator.hasNext()) {
			list.add((BloodGroup) iterator.next());
		}
		return list;
	}

	@Override
	public Registration viewProfile(long phNo) {
		Iterator<Registration> itr = null;
		Registration registration = new Registration();
		try {
			itr = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
					.aggregate("{$match:{phNo:#}}", phNo).and("{$project:{id:0,password:0}}").as(Registration.class);
			if (itr.hasNext()) {
				registration = itr.next();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return registration;
	}

	@Override
	public Response deleteUser(long phNo) {

		Response response = new Response();

		Registration user = new Jongo(DB_Constants.getMongodbDatabase())
				.getCollection(DB_Constants.getRegistrationCol()).findOne("{phNo:#}", phNo).as(Registration.class);
		if (user != null) {
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
					.remove("{phNo:#}", phNo);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("Deleted!");
		} else {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("failed to delete!");
		}
		return response;
	}

	@Override
	public ArrayList<Transaction> transactionList(long phNo) {

		ArrayList<Transaction> list = new ArrayList<Transaction>();

		Iterator iterator = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getTransactionCol())
				.find("{phNo:#}", phNo).as(Transaction.class).iterator();

		while (iterator.hasNext()) {
			list.add((Transaction) iterator.next());
		}
		return list;
	}

	@Override
	public Response updateQuantity(Transaction transaction) {

		Response response = new Response();
		BloodGroup bg = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
				.findOne("{bloodGroup:#}", transaction.getBloodGroup()).as(BloodGroup.class);

		if (bg != null) {
			if (transaction.getStatus().equalsIgnoreCase("donated")) {
			int n = bg.getQuantity() + transaction.getQuantity();
			bg.setQuantity(n);}
			if (transaction.getStatus().equalsIgnoreCase("recieved")) {
				int n=bg.getQuantity()-transaction.getQuantity();
				bg.setQuantity(n);
			}
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getBloodgroupCol())
					.update("{bloodGroup:#}", transaction.getBloodGroup()).upsert().with(bg);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("blood quantity updated");
			insertTransaction(transaction);
		} else {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("failed to update blood quantity");
		}

		return response;
	}

	@Override
	public Response profileEdit(Registration updation, long id) {
		Response response = new Response();

		Registration dbProfile = new Jongo(DB_Constants.getMongodbDatabase())
				.getCollection(DB_Constants.getRegistrationCol()).findOne("{id:#}", id).as(Registration.class);
		updation.setId(id);
		if (dbProfile.getPhNo() == updation.getPhNo()) {
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
					.update("{id:#}", id).upsert().with(updation);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("successfully edited profile");
		} else {
			Registration bg = new Jongo(DB_Constants.getMongodbDatabase())
					.getCollection(DB_Constants.getRegistrationCol()).findOne("{phNo:#}", updation.getPhNo())
					.as(Registration.class);
			if (bg == null) {
				new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
						.update("{id:#}", id).upsert().with(updation);
				response.setStatusCode(ResponseConstants.Success_code);
				response.setStatusMessage("successfully edited profile");
			} else {
				response.setStatusCode(ResponseConstants.Error_code);
				response.setStatusMessage("Failed to update as phone no already exist");
			}
		}
		return response;
	}

	@Override
	public Response changePassword(String newPassword, String oldPassword, long id) {

		Response response = new Response();
		Registration dbprofile = new Jongo(DB_Constants.getMongodbDatabase())
				.getCollection(DB_Constants.getRegistrationCol()).findOne("{id:#}", id).as(Registration.class);
		if (dbprofile != null && oldPassword.equals(dbprofile.getPassword())) {
			dbprofile.setPassword(newPassword);
			new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getRegistrationCol())
					.update("{id:#}", id).upsert().with(dbprofile);
			response.setStatusCode(ResponseConstants.Success_code);
			response.setStatusMessage("password updated successfully");
		} else {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("failed to update password");
		}
		return response;
	}

	@Override
	public List<Role> getAllRoles() throws BloodBankException {
		Iterator<Role> itr = null;
		List<Role> listOfRoles = new ArrayList<Role>();
		try {
			itr = new Jongo(DB_Constants.getMongodbDatabase()).getCollection("roles").aggregate("{$match:{}}")
					.as(Role.class) // "rolesDetails"
					.iterator();
			while (itr.hasNext()) {
				Role role = new Role();
				role = itr.next();
				listOfRoles.add(role);

			}
		} catch (Exception e) {
			throw new BloodBankException("Exception Occure Wille Feacthing The All Roles ", e);
		}
		return listOfRoles;
	}

	@Override
	public UserPermissions getAdminAndUserRoles(int i) throws BloodBankException {

		Iterator<UserPermissions> itr = null;
		UserPermissions userPermissions = new UserPermissions();
		try {
			itr = new Jongo(DB_Constants.getMongodbDatabase()).getCollection("roles")
					.aggregate("{$match:{roleId:#}}", i)
					.and("{$group:{_id:null,permissionList: { $first: '$permissions'},roleIdList: { $push: '$roleId' }}}")
					.as(UserPermissions.class).iterator();
			if (itr.hasNext()) {
				userPermissions = itr.next();
			}
		} catch (Exception e) {
			throw new BloodBankException("Exception Occure Wille Fetching The All Roles", e);
		}
		return userPermissions;
	}

	@Override
	public Login checkAdmin(Login login) {
		Response response = new Response();
		Login user = new Jongo(DB_Constants.getMongodbDatabase()).getCollection(DB_Constants.getAdminCol())
				.findOne("{phNo:#}", login.getPhNo()).as(Login.class);
		if (user != null) {
			String generatedlogedPasswordHash = BCrypt.hashpw(login.getPassword(), BCrypt.gensalt(12));
			boolean b = BCrypt.checkpw(user.getPassword(), generatedlogedPasswordHash);
			if (b) {
				response.setStatusCode(ResponseConstants.Success_code);
				response.setStatusMessage("Successfully logged in!");
			} else {
				response.setStatusCode(ResponseConstants.Error_code);
				response.setStatusMessage("Error while logging ");
			}
		} else {
			response.setStatusCode(ResponseConstants.Error_code);
			response.setStatusMessage("User Not found");
		}
		return user;

	}

}
