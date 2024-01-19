package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;

/**
 * @author Dazo66
 */
public interface KemonoArtistService {

    Page<Artist> getArtistByPage(int page, int pageSize,
                                 Wrapper<Artist> queryWrapper);

    Artist addArtist(Artist artist);

    Artist getArtistId(String artistId, ArtistDomainEnum domain);

    Artist disable(String artistId);

    Artist updateByArtistId(Artist artist);


}
