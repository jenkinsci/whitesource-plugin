package org.whitesource.jenkins.model;

import org.whitesource.agent.api.model.ChecksumType;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the dependency data to be send through the wire
 * Created to solve https://jenkins.io/blog/2018/01/13/jep-200/
 * DependencyInfo does not go through the wire from the master to the slave due to new security policies
 *
 * @author eugen.horovitz
 */
public class RemoteDependency implements Serializable {

    /* --- Members --- */

    private String systemPath ;
    private String artifactId;
    private String sha1;
    private Map<ChecksumType,String> checksums;
    private String otherPlatformSha1;
    private String fullHash;
    private String mostSigBitsHash;

    private String groupId;
    private String version;
    private String type;
    private String classifier;
    private String scope;

    private String leastSigBitsHash;

    /* --- Constructors --- */

    public RemoteDependency() {
        checksums = new HashMap<>();
    }

    /* -- */

    public static Collection<DependencyInfo> convert(Collection<RemoteDependency> remoteDependencies) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();

        for (RemoteDependency remoteDependency : remoteDependencies) {
            DependencyInfo dependencyInfo = new DependencyInfo();
            dependencyInfo.setSystemPath(remoteDependency.getSystemPath());
            dependencyInfo.setArtifactId(remoteDependency.getArtifactId());
            dependencyInfo.setSha1(remoteDependency.getSha1());
            dependencyInfo.setChecksums(remoteDependency.getChecksums());

            dependencyInfo.setOtherPlatformSha1(remoteDependency.getOtherPlatformSha1());
            dependencyInfo.setFullHash(remoteDependency.getFullHash());
            dependencyInfo.setMostSigBitsHash(remoteDependency.getMostSigBitsHash());
            dependencyInfo.setLeastSigBitsHash(remoteDependency.getLeastSigBitsHash());


            dependencyInfo.setGroupId(remoteDependency.getGroupId());
            dependencyInfo.setVersion(remoteDependency.getVersion());
            dependencyInfo.setType(remoteDependency.getType());
            dependencyInfo.setClassifier(remoteDependency.getClassifier());
            dependencyInfo.setScope(remoteDependency.getScope());

            dependencies.add(dependencyInfo);
        }

        return dependencies;
    }

    /* --- Getters/Setters  --- */

    public Map<ChecksumType, String> getChecksums() {
        return checksums;
    }

    public String getSystemPath() {
        return systemPath;
    }

    public void setSystemPath(String systemPath) {
        this.systemPath = systemPath;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public void setOtherPlatformSha1(String otherPlatformSha1) {
        this.otherPlatformSha1 = otherPlatformSha1;
    }

    public String getOtherPlatformSha1() {
        return otherPlatformSha1;
    }

    public void setFullHash(String fullHash) {
        this.fullHash = fullHash;
    }

    public String getFullHash() {
        return fullHash;
    }

    public void setMostSigBitsHash(String mostSigBitsHash) {
        this.mostSigBitsHash = mostSigBitsHash;
    }

    public String getMostSigBitsHash() {
        return mostSigBitsHash;
    }

    public void setLeastSigBitsHash(String leastSigBitsHash) {
        this.leastSigBitsHash = leastSigBitsHash;
    }

    public String getLeastSigBitsHash() {
        return leastSigBitsHash;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getScope() {
        return scope;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
