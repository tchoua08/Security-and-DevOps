package com.example.demo.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.persistence.Cart;
import com.example.demo.model.persistence.User;
import com.example.demo.model.persistence.repositories.CartRepository;
import com.example.demo.model.persistence.repositories.UserRepository;
import com.example.demo.model.requests.CreateUserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/user")
public class UserController {

	private static final Logger log = LoggerFactory.getLogger(UserController.class);
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@GetMapping("/id/{id}")
	public ResponseEntity<User> findById(@PathVariable Long id, Authentication authentication) {
		return userRepository.findById(id)
			.filter(user -> ownsUser(user.getUsername(), authentication))
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}
	
	@GetMapping("/{username}")
	public ResponseEntity<User> findByUserName(@PathVariable String username, Authentication authentication) {
		if (!ownsUser(username, authentication)) {
			return ResponseEntity.status(403).build();
		}
		User user = userRepository.findByUsername(username);
		return user == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(user);
	}
	
	@PostMapping("/create")
	public ResponseEntity<User> createUser(@RequestBody CreateUserRequest createUserRequest) {
		try {
			if (!isValidCreateUserRequest(createUserRequest)) {
				log.warn("CREATE_USER_FAILURE reason=invalid_request username={}", safeUsername(createUserRequest));
				return ResponseEntity.badRequest().build();
			}
			if (userRepository.findByUsername(createUserRequest.getUsername()) != null) {
				log.warn("CREATE_USER_FAILURE reason=duplicate_username username={}", createUserRequest.getUsername());
				return ResponseEntity.badRequest().build();
			}

			User user = new User();
			user.setUsername(createUserRequest.getUsername());
			user.setPassword(passwordEncoder.encode(createUserRequest.getPassword()));
			Cart cart = new Cart();
			cartRepository.save(cart);
			user.setCart(cart);
			userRepository.save(user);
			log.info("CREATE_USER_SUCCESS userId={}", user.getId());
			return ResponseEntity.ok(user);
		} catch (RuntimeException e) {
			log.error("UNHANDLED_EXCEPTION", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	private boolean ownsUser(String username, Authentication authentication) {
		return authentication != null && authentication.getName().equals(username);
	}

	private boolean isValidCreateUserRequest(CreateUserRequest request) {
		return request != null
			&& request.getUsername() != null
			&& !request.getUsername().isBlank()
			&& request.getPassword() != null
			&& request.getPassword().length() >= 7
			&& request.getPassword().equals(request.getConfirmPassword());
	}

	private String safeUsername(CreateUserRequest request) {
		return request == null ? null : request.getUsername();
	}
	
}
