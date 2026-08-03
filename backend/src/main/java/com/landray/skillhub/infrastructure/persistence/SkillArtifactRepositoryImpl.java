package com.landray.skillhub.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.SkillArtifactMapper;
import com.landray.skillhub.infrastructure.persistence.po.SkillArtifactRow;

/** {@link SkillArtifactRepository} 的 MyBatis 实现。 */
@Repository
public class SkillArtifactRepositoryImpl implements SkillArtifactRepository {

    private final SkillArtifactMapper skillArtifactMapper;

    public SkillArtifactRepositoryImpl(SkillArtifactMapper skillArtifactMapper) {
        this.skillArtifactMapper = skillArtifactMapper;
    }

    @Override
    public Optional<SkillArtifact> findBySkillIdAndVersion(long skillId, String version) {
        SkillArtifactRow row = skillArtifactMapper.selectBySkillIdAndVersion(skillId, version);
        return Optional.ofNullable(row).map(SkillArtifactRepositoryImpl::toDomain);
    }

    @Override
    public List<SkillArtifact> findByVersionIds(List<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return List.of();
        }
        return skillArtifactMapper.selectByVersionIds(versionIds).stream()
                .map(SkillArtifactRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public void incrementDownloadCount(long skillId) {
        skillArtifactMapper.incrementDownloadCount(skillId);
    }

    private static SkillArtifact toDomain(SkillArtifactRow row) {
        return new SkillArtifact(
                row.skillVersionId(),
                row.skillId(),
                row.skillName(),
                row.version(),
                row.artifactKey(),
                row.sha256(),
                row.sizeBytes(),
                row.skillStatus(),
                row.certification(),
                row.certifyNote());
    }
}
