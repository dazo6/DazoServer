package com.dazo66.controller;
import com.dazo66.security.CurrentUser;
import com.dazo66.service.DashboardUserService;
import com.dazo66.util.ResultEntity;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
@RestController
public class DashboardAvatarController {
    private final DashboardUserService users;
    private static final byte[] DEFAULT=defaultImage();
    public DashboardAvatarController(DashboardUserService users) {this.users=users;}
    @GetMapping("/api/users/avatar")
    public ResponseEntity<byte[]> avatar(@RequestParam String userId) {
        userId=DashboardUserService.id(userId);users.checkAvatar(userId);return image(users.avatarOf(userId));
    }
    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> icon() {
        CurrentUser u=CurrentUser.get();return image(u==null?null:users.avatarOf(u.userId));
    }
    @PostMapping("/api/auth/me/avatar")
    public ResultEntity<Boolean> upload(@RequestParam MultipartFile file) throws IOException {
        CurrentUser.require();
        if(file.isEmpty() || file.getSize()>2097152) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"图片最大2MiB");
        BufferedImage source;
        try(InputStream input=file.getInputStream(); ImageInputStream stream=ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new IllegalArgumentException("请选择PNG或JPEG图片");
            ImageReader reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new IllegalArgumentException("仅支持PNG和JPEG");
                reader.setInput(stream,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || w>4096 || h>4096 || (long)w*h>16000000) throw new IllegalArgumentException("图片尺寸过大");
                source=reader.read(0);
            } finally {reader.dispose();}
        }
        if(source==null) throw new IllegalArgumentException("图片无效");
        BufferedImage target=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=target.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int side=Math.min(source.getWidth(),source.getHeight()),x=(source.getWidth()-side)/2,y=(source.getHeight()-side)/2;
        g.drawImage(source,0,0,256,256,x,y,x+side,y+side,null);g.dispose();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(target,"png",bytes);
        if(bytes.size()>262144) throw new IllegalArgumentException("标准化头像过大");
        users.avatar("data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()));
        return ResultEntity.successWithData(true);
    }
    private ResponseEntity<byte[]> image(String data) {
        byte[] bytes=DEFAULT;
        if(data!=null && data.startsWith("data:image/png;base64,")) {
            try {bytes=Base64.getDecoder().decode(data.substring(22));}catch(IllegalArgumentException ignored) {}
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore().cachePrivate()).body(bytes);
    }
    private static byte[] defaultImage() {
        try {
            BufferedImage b=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=b.createGraphics();
            g.setColor(new Color(65,101,160));g.fillRoundRect(0,0,64,64,16,16);g.setColor(Color.WHITE);
            g.fillOval(23,12,18,18);g.fillRoundRect(13,35,38,23,20,20);g.dispose();
            ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(b,"png",out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
}
