package com.example.demo.security;

import static com.auth0.jwt.algorithms.Algorithm.HMAC512;

import java.io.IOException;
import java.util.Collections;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class JWTAuthenticationVerificationFilter extends BasicAuthenticationFilter {

	public JWTAuthenticationVerificationFilter(AuthenticationManager authenticationManager) {
		super(authenticationManager);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws IOException, ServletException {
		String header = request.getHeader(SecurityConstants.HEADER_STRING);
		if (header == null || !header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
			chain.doFilter(request, response);
			return;
		}

		try {
			UsernamePasswordAuthenticationToken authentication = getAuthentication(header);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			chain.doFilter(request, response);
		} catch (JWTVerificationException e) {
			SecurityContextHolder.clearContext();
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	private UsernamePasswordAuthenticationToken getAuthentication(String header) {
		String token = header.replace(SecurityConstants.TOKEN_PREFIX, "");
		String username = JWT.require(HMAC512(SecurityConstants.SECRET.getBytes()))
			.build()
			.verify(token)
			.getSubject();
		if (username == null) {
			return null;
		}
		return new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
	}
}
