package com.trailiva.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.trailiva.data.model.*;
import com.trailiva.data.repository.OfficialWorkspaceRepository;
import com.trailiva.data.repository.PersonalWorkspaceRepository;
import com.trailiva.data.repository.RoleRepository;
import com.trailiva.data.repository.UserRepository;
import com.trailiva.web.exceptions.UserException;
import com.trailiva.web.exceptions.WorkspaceException;
import com.trailiva.web.payload.request.WorkspaceRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
@AllArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final ModelMapper modelMapper;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OfficialWorkspaceRepository officialWorkspaceRepository;


    @Override
    public WorkSpace createWorkspace(WorkspaceRequest request, Long userId) throws WorkspaceException, UserException {
        User user = getAUserByUserId(userId);
        WorkSpace createdWorkspace = null;
        if (existByName(request.getName()))
            throw new WorkspaceException("Workspace with name already exist");

        if (WorkSpaceType.PERSONAL == request.getWorkSpaceType())
            createdWorkspace = createPersonalWorkspace(request, user);

        else if (WorkSpaceType.OFFICIAL == request.getWorkSpaceType())
            createdWorkspace = createOfficialWorkspace(request, user);

        return createdWorkspace;
    }

    @Override
    public void addMemberToOfficialWorkspace(List<String> memberEmails, Long userId) throws UserException, WorkspaceException {
        if (!memberEmails.isEmpty()) {
            for (String email : memberEmails) {
                onboardMember(userId, email);
            }
        }

    }

    private void onboardMember(Long userId, String email) throws UserException {
        User user = getAUserByEmail(email);
        User workspaceOwner = getAUserByUserId(userId);
        if (userAlreadyExist(workspaceOwner.getOfficialWorkspace().getMembers(),
                workspaceOwner.getOfficialWorkspace().getModerators(), email, workspaceOwner.getEmail())) {
            throw new UserException("Member with email " + email + " already added to this workspace");
        }
        workspaceOwner.getOfficialWorkspace().getMembers().add(user);
        userRepository.save(workspaceOwner);
    }

    private User getAUserByEmail(String email) throws UserException {
        return userRepository.findByEmail(email).orElseThrow(() -> new UserException("User not found"));
    }

    private User getAUserByUserId(Long id) throws UserException {
        return userRepository.findById(id).orElseThrow(() -> new UserException("User not found"));
    }

    @Override
    public void addModeratorToOfficialWorkspace(List<String> moderatorEmail, Long userId) throws UserException {
        if (!moderatorEmail.isEmpty()) {
            for (String email : moderatorEmail) {
                onboardModerator(userId, email);
            }
        }

    }

    private void onboardModerator(Long userId, String email) throws UserException {
        User user = getAUserByEmail(email);
        User workspaceOwner = getAUserByUserId(userId);
        if (userAlreadyExist(workspaceOwner.getOfficialWorkspace().getMembers(),
                workspaceOwner.getOfficialWorkspace().getModerators(), email, workspaceOwner.getEmail())) {
            throw new UserException("Moderator with email " + email + " already added to this workspace");
        }
        user.getRoles().add(roleRepository.findByName("ROLE_MODERATOR").get());
        workspaceOwner.getOfficialWorkspace().getModerators().add(user);
        userRepository.save(workspaceOwner);
    }


    public void addMemberToWorkspaceFromCSV(MultipartFile file, Long userId) throws IOException,
            CsvValidationException, UserException {
        CSVReader reader = new CSVReader(new FileReader(convertMultiPartToFile(file)));
        String[] nextLine;
        while ((nextLine = reader.readNext()) != null) {
            for (String email : nextLine) {
                onboardMember(userId, email);
            }
        }
    }

    public void addModeratorToWorkspaceFromCSV(MultipartFile file, Long userId) throws IOException,
            CsvValidationException, UserException {
        CSVReader reader = new CSVReader(new FileReader(convertMultiPartToFile(file)));
        String[] nextLine;
        while ((nextLine = reader.readNext()) != null) {
            for (String email : nextLine) {
                onboardModerator(userId, email);
            }
        }
    }


    private WorkSpace createPersonalWorkspace(WorkspaceRequest request, User user) {
        PersonalWorkspace workSpace = modelMapper.map(request, PersonalWorkspace.class);
        PersonalWorkspace space = savePersonalWorkspace(workSpace);
        user.setPersonalWorkspace(space);
        userRepository.save(user);
        return space;
    }

    private WorkSpace createOfficialWorkspace(WorkspaceRequest request, User user) {
        user.getRoles().add(roleRepository.findByName("ROLE_SUPER_MODERATOR").get());
        OfficialWorkspace workSpace = modelMapper.map(request, OfficialWorkspace.class);
        OfficialWorkspace officialWorkspace = saveOfficialWorkspace(workSpace);
        user.setOfficialWorkspace(officialWorkspace);
        userRepository.save(user);
        return officialWorkspace;
    }

    @Override
    public WorkSpace getUserPersonalWorkspace(Long userId) throws UserException {
        User user = getAUserByUserId(userId);
        return user.getPersonalWorkspace();
    }

    public WorkSpace getPersonalWorkspace(Long workspaceId) throws WorkspaceException {
        return personalWorkspaceRepository.findById(workspaceId).orElseThrow(
                () -> new WorkspaceException("Workspace not found"));
    }

    @Override
    public OfficialWorkspace getOfficialWorkspace(Long workspaceId) throws WorkspaceException {
        return officialWorkspaceRepository.findById(workspaceId).orElseThrow(
                () -> new WorkspaceException("Workspace not found"));
    }

    @Override
    public OfficialWorkspace getUserOfficialWorkspace(Long userId) throws WorkspaceException {
        return officialWorkspaceRepository.findById(userId).orElseThrow(
                () -> new WorkspaceException("Workspace not found"));
    }


    private boolean existByName(String name) {
        return personalWorkspaceRepository.existsByName(name)
                || officialWorkspaceRepository.existsByName(name);
    }

    private PersonalWorkspace savePersonalWorkspace(PersonalWorkspace workSpace) {
        return personalWorkspaceRepository.save(workSpace);
    }

    private OfficialWorkspace saveOfficialWorkspace(OfficialWorkspace workSpace) {
        return officialWorkspaceRepository.save(workSpace);
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = new File(Objects.requireNonNull(file.getOriginalFilename()));
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }

    private boolean userAlreadyExist(Set<User> members, Set<User> moderators, String email, String ownerEmail) {
        boolean memberExist = members.stream().anyMatch(user -> user.getEmail().equals(email));
        boolean moderatorExist = moderators.stream().anyMatch(user -> user.getEmail().equals(email));
        return memberExist || moderatorExist || ownerEmail.equals(email);
    }
}
