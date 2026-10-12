package com.buc.ysc.file.mapper;

import com.buc.ysc.file.vo.StoredFile;

public interface FileMapper {
    Long nextFileSeq();

    int insertFileBase(StoredFile file);

    int insertFileDetail(StoredFile file);

    StoredFile selectFile(Long filSeq);

    int deleteFileDetails(Long filSeq);

    int deleteFileBase(Long filSeq);
}
