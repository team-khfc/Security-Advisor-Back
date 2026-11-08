package kr.securityadvisor.request.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬 파일 저장(저장 폴더 + UUID 파일명). 경로는 서버가 만든 이름만 쓰므로 경로 조작이 불가능하다. */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${securityadvisor.storage.dir:./storage}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public void save(String storedName, InputStream in) {
        try {
            Files.createDirectories(root);
            Files.copy(in, resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + storedName, e);
        }
    }

    public Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("저장 폴더 밖 경로입니다");
        }
        return path;
    }

    public boolean exists(String storedName) {
        return Files.exists(resolve(storedName));
    }
}
