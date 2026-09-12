package com.dazo66.test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

public class TestJsoup {

    @Test
    public void testParse() {
        String html = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \" http://www.w3.org/TR/html4/loose.dtd \"> \n" +
                " <head> \n" +
                "     <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"> \n" +
                "     <meta name=\"viewport\" content=\"width=512\"> \n" +
                "     <meta name=\"robots\" content=\"noindex, nofollow\"> \n" +
                "     <title>D:\\OneDrive - Everything</title> \n" +
                "     <link rel=\"stylesheet\" href=\"/main.css\" type=\"text/css\"> \n" +
                "     <link rel=\"shortcut icon\" href=\"/favicon.ico\" type=\"image/x-icon\"> \n" +
                " </head> \n" +
                " <body> \n" +
                "     <center> \n" +
                "         <br> \n" +
                "         <br> \n" +
                "         <a href=\"/\"> \n" +
                "             <img class=\"logo\" src=\"/Everything.gif\" alt=\"Everything\"> \n" +
                "         </a> \n" +
                "         <br> \n" +
                "         <br> \n" +
                "         <form id=\"searchform\" action=\"/\" method=\"get\"> \n" +
                "             <input class=\"searchbox\" style=\"width:480px\" id=\"search\" name=\"search\" type=\"text\" onfocus=\"this.select()\" title=\"搜索 Everything\" value=\"\" autofocus> \n" +
                "             <input type=\"hidden\" name=\"sort\" value=\"name\"/> \n" +
                "             <input type=\"hidden\" name=\"ascending\" value=\"0\"/> \n" +
                "         </form> \n" +
                "         <table cellspacing=\"0\" width=\"480px\"> \n" +
                "             <tr> \n" +
                "                 <td colspan=\"3\"> \n" +
                "                     <p class=\"indexof\">索引 D:\\OneDrive</p> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                "             <tr> \n" +
                "                 <td class=\"updir\" colspan=\"3\"> \n" +
                "                     <a href=\"/\"> \n" +
                "                         <img class=\"icon\" src=\"/updir.gif\" alt=\"\">上一目录.. \n" +
                "                     </a> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                "             <tr> \n" +
                "                 <td class=\"nameheader\"> \n" +
                "                     <a href=\"/D%3A/OneDrive?sort=name&amp;ascending=1\"> \n" +
                "                         <span class=\"nobr\"> \n" +
                "                             <nobr> \n" +
                "                                 名称<img class=\"updown\" src=\"/down.gif\" alt=\"\"> \n" +
                "                             </nobr> \n" +
                "                         </span> \n" +
                "                     </a> \n" +
                "                 </td> \n" +
                "                 <td class=\"sizeheader\"> \n" +
                "                     <a href=\"/D%3A/OneDrive?sort=size&amp;ascending=0\"> \n" +
                "                         <span class=\"nobr\"> \n" +
                "                             <nobr>大小</nobr> \n" +
                "                         </span> \n" +
                "                     </a> \n" +
                "                 </td> \n" +
                "                 <td class=\"modifiedheader\"> \n" +
                "                     <a href=\"/D%3A/OneDrive?sort=date_modified&amp;ascending=0\"> \n" +
                "                         <span class=\"nobr\"> \n" +
                "                             <nobr>修改日期</nobr> \n" +
                "                         </span> \n" +
                "                     </a> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                "             <tr> \n" +
                "                 <td colspan=\"3\" class=\"lineshadow\" height=\"1\"></td> \n" +
                "             </tr> \n" +
                "             <tr class=\"trdata1\"> \n" +
                "                 <td class=\"file\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <a href=\"/D%3A/OneDrive/Personal%20Vault-DESKTOP-OES6R2E.lnk\"> \n" +
                "                                 <img class=\"icon\" src=\"/file.gif\" alt=\"\">Personal Vault-DESKTOP-OES6R2E.lnk \n" +
                "                             </a> \n" +
                "                         </nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"sizedata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr>2 KB</nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"modifieddata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <span class=\"nobr\"> \n" +
                "                                 <nobr>2025/12/27 0:59</nobr> \n" +
                "                             </span> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                "             <tr class=\"trdata2\"> \n" +
                "                 <td class=\"folder\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <a href=\"/D%3A/OneDrive/龙龙专用\"> \n" +
                "                                 <img class=\"icon\" src=\"/folder.gif\" alt=\"\">龙龙专用 \n" +
                "                             </a> \n" +
                "                         </nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"sizedata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr></nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"modifieddata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <span class=\"nobr\"> \n" +
                "                                 <nobr>2025/12/28 6:30</nobr> \n" +
                "                             </span> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                "             <tr class=\"trdata1\"> \n" +
                "                 <td class=\"folder\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <a href=\"/D%3A/OneDrive/附件\"> \n" +
                "                                 <img class=\"icon\" src=\"/folder.gif\" alt=\"\">附件 \n" +
                "                             </a> \n" +
                "                         </nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"sizedata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr></nobr> \n" +
                "                     </span> \n" +
                "                 </td> \n" +
                "                 <td class=\"modifieddata\"> \n" +
                "                     <span class=\"nobr\"> \n" +
                "                         <nobr> \n" +
                "                             <span class=\"nobr\"> \n" +
                "                                 <nobr>2025/5/18 8:23</nobr> \n" +
                "                             </span> \n" +
                "                 </td> \n" +
                "             </tr> \n" +
                " \n" +
                "         </table> \n" +
                "     </center> \n" +
                " </body>";

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table tr");
        for (Element row : rows) {
            // Check if it's a file or folder row
            Element fileCell = row.select("td.file, td.folder").first();
            if (fileCell != null) {
                boolean isFolder = fileCell.hasClass("folder");
                String name = fileCell.select("a").text();
                String href = fileCell.select("a").attr("href");
                String size = row.select("td.sizedata").text();
                String modified = row.select("td.modifieddata").text();

                System.out.println("Name: " + name);
                System.out.println("IsFolder: " + isFolder);
                System.out.println("Href: " + href);
                System.out.println("Size: " + size);
                System.out.println("Modified: " + modified);
                System.out.println("----------------");
            }
        }
    }
}
