package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.KemonoArtist;
import com.dazo66.mapper.FanboxArtistMapper;
import com.dazo66.service.KemonoArtistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Dazo66
 */
@Service
public class KemonoArtistServiceImpl implements KemonoArtistService {

    @Autowired
    private FanboxArtistMapper fanboxArtistMapper;

    @Override
    public Page<KemonoArtist> getArtistByPage(int page, int pageSize,
                                              Wrapper<KemonoArtist> queryWrapper) {
        Page<KemonoArtist> fanboxArtistPage = Page.of(page, pageSize);
        return fanboxArtistMapper.selectPage(fanboxArtistPage, queryWrapper);
    }

    @Override
    public KemonoArtist addArtist(KemonoArtist artist) {
        fanboxArtistMapper.insert(artist);
        return artist;
    }

    @Override
    public KemonoArtist getArtistId(String artistId) {
        return fanboxArtistMapper.selectOne(new QueryWrapper<>(new KemonoArtist().setArtistId(artistId)));
    }

    @Override
    public KemonoArtist disable(String artistId) {
        KemonoArtist kemonoArtist = new KemonoArtist().setArtistId(artistId).setEnable(false);
        fanboxArtistMapper.update(kemonoArtist, new QueryWrapper<KemonoArtist>().eq("artist_id",
                artistId));
        return kemonoArtist;
    }

    @Override
    public KemonoArtist updateByArtistId(KemonoArtist artist) {
        if (artist.getArtistId() == null) {
            throw new RuntimeException("artist id must not null in update");
        }
        fanboxArtistMapper.update(artist, new QueryWrapper<KemonoArtist>().eq("artist_id", artist.getArtistId()));
        return artist;
    }
}
