package com.example.demo;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.demo.controllers.CartController;
import com.example.demo.controllers.UserController;
import com.example.demo.model.persistence.Cart;
import com.example.demo.model.persistence.Item;
import com.example.demo.model.persistence.User;
import com.example.demo.model.persistence.UserOrder;
import com.example.demo.model.persistence.repositories.CartRepository;
import com.example.demo.model.persistence.repositories.ItemRepository;
import com.example.demo.model.persistence.repositories.UserRepository;
import com.example.demo.model.requests.CreateUserRequest;
import com.example.demo.model.requests.ModifyCartRequest;
import com.example.demo.security.SecurityConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
public class SareetaApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void contextLoads() {
	}

	@Test
	public void createUserStoresPasswordHashAndDoesNotReturnPassword() throws Exception {
		MvcResult result = createUser("alice", "password1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andReturn();

		String response = result.getResponse().getContentAsString();
		assertEquals(false, response.contains("password1"));
	}

	@Test
	public void createUserRejectsShortPasswordAndMismatchedConfirmation() throws Exception {
		mockMvc.perform(post("/api/user/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"shortpass\",\"password\":\"short\",\"confirmPassword\":\"short\"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/user/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"mismatch\",\"password\":\"password1\",\"confirmPassword\":\"password2\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	public void protectedEndpointsRequireToken() throws Exception {
		mockMvc.perform(get("/api/item"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	public void loginReturnsBearerTokenForValidUser() throws Exception {
		createUser("bob", "password1").andExpect(status().isOk());

		mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"bob\",\"password\":\"password1\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string(SecurityConstants.HEADER_STRING, containsString(SecurityConstants.TOKEN_PREFIX)));
	}

	@Test
	public void loginRejectsInvalidCredentials() throws Exception {
		createUser("badlogin", "password1").andExpect(status().isOk());

		mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"badlogin\",\"password\":\"wrongpass\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	public void userCanReadOwnProfileButNotAnotherUsersProfile() throws Exception {
		createUser("carol", "password1").andExpect(status().isOk());
		createUser("dave", "password1").andExpect(status().isOk());
		String carolToken = login("carol", "password1");

		mockMvc.perform(get("/api/user/carol")
				.header(SecurityConstants.HEADER_STRING, carolToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("carol"))
			.andExpect(jsonPath("$.password").doesNotExist());

		mockMvc.perform(get("/api/user/dave")
				.header(SecurityConstants.HEADER_STRING, carolToken))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/user/id/99999")
				.header(SecurityConstants.HEADER_STRING, carolToken))
			.andExpect(status().isNotFound());
	}

	@Test
	public void userCanReadOwnProfileById() throws Exception {
		MvcResult result = createUser("iduser", "password1")
			.andExpect(status().isOk())
			.andReturn();
		String token = login("iduser", "password1");
		String id = result.getResponse().getContentAsString().replaceAll(".*\"id\":([0-9]+).*", "$1");

		mockMvc.perform(get("/api/user/id/" + id)
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("iduser"));
	}

	@Test
	public void authenticatedUserCanManageCartAndSubmitOrder() throws Exception {
		createUser("erin", "password1").andExpect(status().isOk());
		String token = login("erin", "password1");

		mockMvc.perform(get("/api/item")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));

		mockMvc.perform(post("/api/cart/addToCart")
				.header(SecurityConstants.HEADER_STRING, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"erin\",\"itemId\":1,\"quantity\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.total").value(5.98));

		mockMvc.perform(post("/api/cart/removeFromCart")
				.header(SecurityConstants.HEADER_STRING, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"erin\",\"itemId\":1,\"quantity\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.total").value(2.99));

		mockMvc.perform(post("/api/order/submit/erin")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.total").value(2.99));

		mockMvc.perform(get("/api/order/history/erin")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	public void authenticatedItemLookupsReturnFoundAndNotFound() throws Exception {
		createUser("itemreader", "password1").andExpect(status().isOk());
		String token = login("itemreader", "password1");

		mockMvc.perform(get("/api/item/1")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Round Widget"));

		mockMvc.perform(get("/api/item/99999")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/item/name/Round Widget")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(get("/api/item/name/Missing Widget")
				.header(SecurityConstants.HEADER_STRING, token))
			.andExpect(status().isNotFound());
	}

	@Test
	public void authenticatedCartAndOrderMissingResourcesReturnNotFound() throws Exception {
		String missingUserToken = jwtFor("ghost");

		mockMvc.perform(post("/api/cart/addToCart")
				.header(SecurityConstants.HEADER_STRING, missingUserToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"ghost\",\"itemId\":1,\"quantity\":1}"))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/order/submit/ghost")
				.header(SecurityConstants.HEADER_STRING, missingUserToken))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/order/history/ghost")
				.header(SecurityConstants.HEADER_STRING, missingUserToken))
			.andExpect(status().isNotFound());
	}

	@Test
	public void authenticatedCartMissingItemReturnsNotFound() throws Exception {
		createUser("missingitem", "password1").andExpect(status().isOk());
		String token = login("missingitem", "password1");

		mockMvc.perform(post("/api/cart/addToCart")
				.header(SecurityConstants.HEADER_STRING, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"missingitem\",\"itemId\":99999,\"quantity\":1}"))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/cart/removeFromCart")
				.header(SecurityConstants.HEADER_STRING, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"missingitem\",\"itemId\":99999,\"quantity\":1}"))
			.andExpect(status().isNotFound());
	}

	@Test
	public void authenticatedUserCannotModifyAnotherUsersCartOrOrders() throws Exception {
		createUser("frank", "password1").andExpect(status().isOk());
		createUser("gina", "password1").andExpect(status().isOk());
		String frankToken = login("frank", "password1");

		mockMvc.perform(post("/api/cart/addToCart")
				.header(SecurityConstants.HEADER_STRING, frankToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"gina\",\"itemId\":1,\"quantity\":1}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/order/history/gina")
				.header(SecurityConstants.HEADER_STRING, frankToken))
			.andExpect(status().isForbidden());
	}

	@Test
	public void cartAndOrderModelTotalsAreCalculated() {
		Item item = new Item();
		item.setId(99L);
		item.setName("Widget");
		item.setDescription("Test widget");
		item.setPrice(new BigDecimal("3.50"));

		User user = new User();
		user.setUsername("henry");
		user.setPassword("hashed");

		Cart cart = new Cart();
		cart.setUser(user);
		user.setCart(cart);
		cart.addItem(item);
		cart.addItem(item);
		cart.removeItem(item);

		assertEquals(new BigDecimal("3.50"), cart.getTotal());
		assertEquals(1, cart.getItems().size());

		UserOrder order = UserOrder.createFromCart(cart);
		assertNotNull(order.getItems());
		assertEquals(user, order.getUser());
		assertEquals(new BigDecimal("3.50"), order.getTotal());
	}

	@Test
	public void modelAccessorsAndEqualityBranchesAreCovered() {
		Item item = new Item();
		item.setId(7L);
		item.setName("Accessor Widget");
		item.setDescription("Accessor description");
		item.setPrice(new BigDecimal("4.25"));

		Item same = new Item();
		same.setId(7L);
		Item different = new Item();
		different.setId(8L);
		Item nullId = new Item();
		Item otherNullId = new Item();

		assertEquals(7L, item.getId());
		assertEquals("Accessor Widget", item.getName());
		assertEquals("Accessor description", item.getDescription());
		assertEquals(new BigDecimal("4.25"), item.getPrice());
		assertEquals(item.hashCode(), same.hashCode());
		assertTrue(item.equals(item));
		assertTrue(item.equals(same));
		assertTrue(nullId.equals(otherNullId));
		assertFalse(item.equals(different));
		assertFalse(item.equals(null));
		assertFalse(item.equals("not-an-item"));
		assertFalse(nullId.equals(item));

		User user = new User();
		user.setId(10L);
		user.setUsername("accessor");
		user.setPassword("hashed");
		assertEquals(10L, user.getId());
		assertEquals("accessor", user.getUsername());
		assertEquals("hashed", user.getPassword());

		Cart cart = new Cart();
		cart.setId(11L);
		cart.setItems(new ArrayList<>());
		cart.setTotal(BigDecimal.ZERO);
		cart.removeItem(item);
		assertEquals(11L, cart.getId());
		assertEquals(new BigDecimal("-4.25"), cart.getTotal());

		UserOrder order = UserOrder.createFromCart(new Cart());
		order.setId(12L);
		order.setItems(Collections.singletonList(item));
		order.setTotal(new BigDecimal("4.25"));
		order.setUser(user);
		assertEquals(12L, order.getId());
		assertEquals(1, order.getItems().size());
		assertEquals(new BigDecimal("4.25"), order.getTotal());
		assertEquals(user, order.getUser());
	}

	@Test
	public void requestAccessorsAreCovered() {
		CreateUserRequest create = new CreateUserRequest();
		create.setUsername("request-user");
		create.setPassword("password1");
		create.setConfirmPassword("password1");
		assertEquals("request-user", create.getUsername());
		assertEquals("password1", create.getPassword());
		assertEquals("password1", create.getConfirmPassword());

		ModifyCartRequest modify = new ModifyCartRequest();
		modify.setUsername("request-user");
		modify.setItemId(3L);
		modify.setQuantity(4);
		assertEquals("request-user", modify.getUsername());
		assertEquals(3L, modify.getItemId());
		assertEquals(4, modify.getQuantity());
	}

	@Test
	public void userControllerDuplicateInvalidAndExceptionBranchesAreCovered() {
		UserController controller = new UserController();
		UserRepository userRepository = mock(UserRepository.class);
		CartRepository cartRepository = mock(CartRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		ReflectionTestUtils.setField(controller, "userRepository", userRepository);
		ReflectionTestUtils.setField(controller, "cartRepository", cartRepository);
		ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);

		assertEquals(400, controller.createUser(null).getStatusCode().value());

		CreateUserRequest duplicate = new CreateUserRequest();
		duplicate.setUsername("duplicate");
		duplicate.setPassword("password1");
		duplicate.setConfirmPassword("password1");
		when(userRepository.findByUsername("duplicate")).thenReturn(new User());
		assertEquals(400, controller.createUser(duplicate).getStatusCode().value());

		CreateUserRequest exploding = new CreateUserRequest();
		exploding.setUsername("explode");
		exploding.setPassword("password1");
		exploding.setConfirmPassword("password1");
		when(userRepository.findByUsername("explode")).thenReturn(null);
		when(passwordEncoder.encode("password1")).thenThrow(new RuntimeException("boom"));
		assertEquals(500, controller.createUser(exploding).getStatusCode().value());
	}

	@Test
	public void cartControllerUnitBranchesAreCovered() {
		CartController controller = new CartController();
		UserRepository userRepository = mock(UserRepository.class);
		CartRepository cartRepository = mock(CartRepository.class);
		ItemRepository itemRepository = mock(ItemRepository.class);
		ReflectionTestUtils.setField(controller, "userRepository", userRepository);
		ReflectionTestUtils.setField(controller, "cartRepository", cartRepository);
		ReflectionTestUtils.setField(controller, "itemRepository", itemRepository);

		ModifyCartRequest request = new ModifyCartRequest();
		request.setUsername("unit");
		request.setItemId(1L);
		request.setQuantity(1);
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("unit");

		when(userRepository.findByUsername("unit")).thenReturn(null);
		assertEquals(404, controller.addTocart(request, authentication).getStatusCode().value());

		User user = new User();
		user.setUsername("unit");
		user.setCart(new Cart());
		when(userRepository.findByUsername("unit")).thenReturn(user);
		when(itemRepository.findById(1L)).thenReturn(Optional.empty());
		assertEquals(404, controller.removeFromcart(request, authentication).getStatusCode().value());

		when(itemRepository.findById(1L)).thenReturn(Optional.of(item("Unit", "1.00")));
		when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ResponseEntity<Cart> response = controller.removeFromcart(request, authentication);
		assertEquals(200, response.getStatusCode().value());
		assertEquals(new BigDecimal("-1.00"), response.getBody().getTotal());
	}

	private org.springframework.test.web.servlet.ResultActions createUser(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/user/create")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"" + password
				+ "\",\"confirmPassword\":\"" + password + "\"}"));
	}

	private String login(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getHeader(SecurityConstants.HEADER_STRING);
	}

	private String jwtFor(String username) {
		return SecurityConstants.TOKEN_PREFIX + JWT.create()
			.withSubject(username)
			.sign(Algorithm.HMAC512(SecurityConstants.SECRET.getBytes()));
	}

	private Item item(String name, String price) {
		Item item = new Item();
		item.setId(1000L);
		item.setName(name);
		item.setDescription(name + " description");
		item.setPrice(new BigDecimal(price));
		return item;
	}
}
