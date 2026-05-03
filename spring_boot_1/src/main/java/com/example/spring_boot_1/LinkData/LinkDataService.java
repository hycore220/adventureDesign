package com.example.spring_boot_1.LinkData;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LinkDataService {

    private final LinkDataRepository linkDataRepository;

    public void create(String link, String userName, String PARAStatus) {
        LinkData linkData = new LinkData();
        linkData.setLink(link);
        linkData.setUserName(userName);
        linkData.setPARAStatus(PARAStatus);
        linkDataRepository.save(linkData);
    }

    public List<LinkData> getByUserName(String userName) {
        return linkDataRepository.findByUserName(userName);
    }

    // 수정
    public void update(int id, String link, String userName, String PARAStatus) {
        LinkData linkData = linkDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("없는 id입니다."));
        linkData.setLink(link);
        linkData.setUserName(userName);
        linkData.setPARAStatus(PARAStatus);
        linkDataRepository.save(linkData);
    }

    // 삭제
    public void delete(int id) {
        linkDataRepository.deleteById(id);
    }
}