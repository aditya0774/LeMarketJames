package com.lemarketjames.profile;

import com.lemarketjames.profile.dto.ProfileDto;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/profile — the authenticated client's own profile + account info. */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileDto getOwnProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return profileService.getOwnProfile(username);
    }
}
