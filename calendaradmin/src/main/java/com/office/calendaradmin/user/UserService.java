package com.office.calendaradmin.user;

import com.office.calendaradmin.user.jpa.UserAuthorityEntity;
import com.office.calendaradmin.user.jpa.UserEntity;
import com.office.calendaradmin.user.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    final public static int UPDATE_USER_AUTHORITY_SUCCESS   = 1;
    final public static int UPDATE_USER_AUTHORITY_FAIL      = 0;

    final private UserRepository userRepository;

    public Map<String, Object> users() {
        log.info("users()");

        Map<String, Object> resultMap = new HashMap<>();

        List<UserEntity> userEntities = userRepository.findAll();
        List<UserDto> users = userEntities.stream()
                .map(UserEntity::toDto)
                .collect(Collectors.toList());

        log.info("users: {}", users);

        resultMap.put("users", users);

        return resultMap;

    }

    @Transactional
    public Map<String, Object> updateUserAuthority(int userNo, Integer authorityNo) {
        log.info("updateUserAuthority()");

        Map<String, Object> resultMap = new HashMap<>();

        Optional<UserEntity> optionalUser = userRepository.findById(userNo);
        if (optionalUser.isPresent()) {
            UserEntity userEntity = optionalUser.get();

            Byte targetAuthorityNo = 1;
            if (authorityNo == 2)
                targetAuthorityNo = 2;

            userEntity.setAuthorityEntity(UserAuthorityEntity.builder()
                    .authNo(targetAuthorityNo)
                    .build());

            userRepository.saveAndFlush(userEntity);
            resultMap.put("resultMap", UPDATE_USER_AUTHORITY_SUCCESS);
            resultMap.put("mod_date", userEntity.toDto().getMod_date());

        } else {
            resultMap.put("resultMap", UPDATE_USER_AUTHORITY_FAIL);

        }
        return resultMap;
    }
}
