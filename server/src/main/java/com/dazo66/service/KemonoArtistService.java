package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.KemonoArtist;

/**
 * @author Dazo66
 */
public interface KemonoArtistService {

    Page<KemonoArtist> getArtistByPage(int page, int pageSize,
                                       Wrapper<KemonoArtist> queryWrapper);

    KemonoArtist addArtist(KemonoArtist artist);

    KemonoArtist getArtistId(String artistId);

    KemonoArtist disable(String artistId);

    KemonoArtist updateByArtistId(KemonoArtist artist);


}
