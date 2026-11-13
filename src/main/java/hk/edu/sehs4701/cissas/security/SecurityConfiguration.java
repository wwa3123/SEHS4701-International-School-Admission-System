package hk.edu.sehs4701.cissas.security;

import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

/**
 * Browser-only SSR: public registration/academic-year pages, role-protected staff/member pages,
 * and CSRF-protected POST forms. Former /api routes are not allowed.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, MemberRepository members,
                                            AdminAccountService accounts) throws Exception {
        return http
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .addFilterAfter(new SessionAuthenticationFilter(members, accounts), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login",
                                "/membership/register", "/membership/verify-email",
                                "/membership/complete-registration", "/membership/success",
                                "/academic-years", "/academic-years/open-for-application",
                                "/academic-years/{id}", "/academic-years/{id}/programme-levels",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login", "/logout",
                                "/membership/register", "/membership/verify-email",
                                "/membership/complete-registration", "/membership/resend-verification").permitAll()
                        .requestMatchers("/member/**").hasRole("MEMBER")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendRedirect(request.getContextPath() + "/login"))
                        .accessDeniedHandler((request, response, ex) -> response.sendError(403)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .build();
    }

    private static final class SessionAuthenticationFilter extends OncePerRequestFilter {
        private final MemberRepository members;
        private final AdminAccountService accounts;

        private SessionAuthenticationFilter(MemberRepository members, AdminAccountService accounts) {
            this.members = members;
            this.accounts = accounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("authenticatedUserId") instanceof Long id
                    && session.getAttribute("authenticatedUserRole") instanceof String role) {
                boolean valid = false;
                if (ActorRole.ROLE_MEMBER.name().equals(role)) {
                    valid = members.findById(id)
                            .filter(member -> member.getAccountStatus() == AccountStatus.ACTIVE).isPresent();
                } else if (ActorRole.ROLE_ADMIN.name().equals(role)
                        || ActorRole.ROLE_SUPER_ADMIN.name().equals(role)) {
                    try {
                        if ("/admin/password".equals(request.getRequestURI().substring(request.getContextPath().length()))
                                && ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod()))) {
                            accounts.requirePasswordSession(request);
                        } else {
                            accounts.requireAdmin(request);
                        }
                        valid = true;
                    } catch (ResponseStatusException ex) {
                        if (ex.getStatusCode().value() != 401 && ex.getStatusCode().value() != 403) throw ex;
                    }
                }
                if (valid) {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(id, null,
                                    List.of(new SimpleGrantedAuthority(role))));
                }
            }
            chain.doFilter(request, response);
        }
    }
}
