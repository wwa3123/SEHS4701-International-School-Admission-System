package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MemberIdentityDocumentRequest;
import hk.edu.sehs4701.cissas.dto.response.MemberIdentityDocument;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MemberIdentityDocumentService {
    private final MemberRepository members;
    private final IdentityDocumentRepository documents;
    private final IdentityDocumentService identityDocuments;
    private final IdentityReviewLockRepository lock;

    public MemberIdentityDocumentService(MemberRepository members, IdentityDocumentRepository documents,
                                         IdentityDocumentService identityDocuments, IdentityReviewLockRepository lock) {
        this.members = members;
        this.documents = documents;
        this.identityDocuments = identityDocuments;
        this.lock = lock;
    }

    @Transactional(readOnly = true)
    public List<MemberIdentityDocument> list(Long memberId) {
        List<IdentityDocument> linked = documents.findAllByMember_IdOrderByIdAsc(memberId);
        boolean hasPrimary = linked.stream().anyMatch(IdentityDocument::isPrimaryDocument);
        return linked.stream().map(document -> {
            return new MemberIdentityDocument(document.getId(), document.getDocumentType(),
                    mask(document.getDocumentNumber()), document.getIssuingCountry(),
                    document.isPrimaryDocument() || (!hasPrimary && document == linked.getFirst()));
        }).toList();
    }

    @Transactional
    public void add(Long memberId, MemberIdentityDocumentRequest request) {
        lock.acquire();
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        List<IdentityDocument> linked = documents.findAllByMember_IdOrderByIdAsc(memberId);
        if (!linked.isEmpty() && linked.stream().noneMatch(IdentityDocument::isPrimaryDocument)) {
            linked.getFirst().setPrimaryDocument(true);
        }
        IdentityDocument document = identityDocuments.createIdentityDocument(
                request.documentType(), request.documentNumber(), request.issuingCountry());
        member.addIdentityDocument(document);
        documents.save(document);
    }

    @Transactional
    public void setPrimary(Long memberId, Long documentId) {
        lock.acquire();
        members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        List<IdentityDocument> linked = documents.findAllByMember_IdOrderByIdAsc(memberId);
        if (linked.stream().noneMatch(document -> document.getId().equals(documentId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity document not found");
        }
        linked.forEach(document -> document.setPrimaryDocument(document.getId().equals(documentId)));
        documents.saveAll(linked);
    }

    private String mask(String number) {
        return number.length() <= 2 ? "****" : "****" + number.substring(number.length() - 2);
    }
}
