package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.FanboxArtist;

/**
 * @author Dazo66
 */
public interface FanboxArtistService {

    Page<FanboxArtist> getArtistByPage(int page, int pageSize,
                                       Wrapper<FanboxArtist> queryWrapper);

    FanboxArtist addArtist(FanboxArtist artist);

    FanboxArtist getArtistId(String artistId);

    FanboxArtist disable(String artistId);

    FanboxArtist updateByArtistId(FanboxArtist artist);


}
