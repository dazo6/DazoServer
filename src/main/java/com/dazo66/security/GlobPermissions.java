package com.dazo66.security;
import java.util.*;
/** Glob language containment via on-demand determinization of union NFAs. */
public final class GlobPermissions {
    private GlobPermissions() {}
    public static String canonical(String p) {
        if(p==null) return "";
        p=p.trim().replace('\\','/');
        if (!p.startsWith("op:") && !p.equals("*")) p=p.toLowerCase(Locale.ROOT);
        return p.equals("/") || p.matches("(?i)^[a-z]:/$") ? p : p.replaceAll("/+$", "");
    }
    public static List<String> normalize(List<String> rules) {
        if(rules==null || rules.size()>200) throw new IllegalArgumentException("权限最多200条");
        Set<String> result=new LinkedHashSet<>();
        for(String raw:rules) {
            if(raw==null) throw new IllegalArgumentException("权限不能为空");
            int split=raw.indexOf(':');
            if(split<1) throw new IllegalArgumentException("权限格式应为type:point");
            String t=raw.substring(0,split).trim().toLowerCase(Locale.ROOT);
            if(!Arrays.asList("read","update","delete").contains(t)) throw new IllegalArgumentException("未知权限类型");
            String originalPoint=raw.substring(split+1);
            String p=canonical(originalPoint);
            if(p.startsWith("//") || p.matches("(?i)^[a-z]:/.*:.*") || p.isEmpty() || originalPoint.length()>512 || p.chars().filter(c->c=='*').count()>16
                || originalPoint.chars().anyMatch(c->c<32) || Arrays.asList(p.split("/")).contains(".."))
                throw new IllegalArgumentException("权限点格式无效");
            if(!p.equals("*") && !p.startsWith("op:") && !p.matches("(?i)^[a-z]:/.*") && !p.startsWith("/"))
                throw new IllegalArgumentException("权限点必须是op:操作点或绝对路径");
            result.add(t+":"+originalPoint);
        }
        return new ArrayList<>(result);
    }
    public static boolean matches(String pattern,String point) {
        pattern=canonical(pattern); point=canonical(point);
        int i=0,j=0,star=-1,mark=0;
        while(j<point.length()) {
            if(i<pattern.length() && pattern.charAt(i)==point.charAt(j)) { i++;j++; }
            else if(i<pattern.length() && pattern.charAt(i)=='*') {star=i++;mark=j;}
            else if(star>=0) {i=star+1;j=++mark;}
            else return false;
        }
        while(i<pattern.length() && pattern.charAt(i)=='*') i++;
        return i==pattern.length();
    }
    public static boolean allows(List<String> rules,AclType type,String point) {
        String prefix=type.name().toLowerCase(Locale.ROOT)+":";
        for(String r:rules) if(r.startsWith(prefix) && matches(r.substring(prefix.length()),point)) return true;
        return false;
    }
    public static boolean subset(List<String> child,List<String> parent) {
        for(AclType type:AclType.values()) if(!contained(patterns(child,type),patterns(parent,type))) return false;
        return true;
    }
    public static boolean strictSubset(List<String> child,List<String> parent) {
        return subset(child,parent) && !subset(parent,child);
    }
    private static List<String> patterns(List<String> rules,AclType t) {
        List<String> out=new ArrayList<>(); String prefix=t.name().toLowerCase(Locale.ROOT)+":";
        for(String r:rules) if(r.startsWith(prefix)) out.add(canonical(r.substring(prefix.length())));
        return out;
    }
    private static final class Nfa {
        final List<Character> chars=new ArrayList<>();
        final BitSet starts=new BitSet(),ends=new BitSet();
        Nfa(List<String> patterns) {
            for(String p:patterns) {
                starts.set(chars.size());
                for(char c:p.toCharArray()) chars.add(c);
                ends.set(chars.size());chars.add(null);
            }
        }
        BitSet close(BitSet s) {
            BitSet r=(BitSet)s.clone();
            for(int i=r.nextSetBit(0);i>=0;i=r.nextSetBit(i+1))
                if(chars.get(i)!=null && chars.get(i)=='*') r.set(i+1);
            return r;
        }
        BitSet move(BitSet s,char c) {
            BitSet next=new BitSet();
            for(int i=s.nextSetBit(0);i>=0;i=s.nextSetBit(i+1)) {
                Character x=chars.get(i);
                if(x!=null) { if(x=='*') next.set(i); else if(x==c) next.set(i+1); }
            }
            return close(next);
        }
        boolean accepts(BitSet s) {return s.intersects(ends);}
    }
    private static boolean contained(List<String> child,List<String> parent) {
        if(child.isEmpty() || parent.contains("*")) return true;
        if(parent.isEmpty()) return false;
        Nfa a=new Nfa(child),b=new Nfa(parent);
        Set<Character> alphabet=new HashSet<>();
        for(Character c:a.chars) if(c!=null && c!='*') alphabet.add(c);
        for(Character c:b.chars) if(c!=null && c!='*') alphabet.add(c);
        char other=1; while(alphabet.contains(other)) other++;
        alphabet.add(other);
        Deque<BitSet[]> queue=new ArrayDeque<>();Set<String> visited=new HashSet<>();
        queue.add(new BitSet[]{a.close(a.starts),b.close(b.starts)});
        long deadline=System.nanoTime()+500_000_000L;
        while(!queue.isEmpty()) {
            BitSet[] pair=queue.remove();String key=pair[0].toString()+"|"+pair[1].toString();
            if(!visited.add(key)) continue;
            if(visited.size()>20000 || System.nanoTime()>deadline)
                throw new IllegalArgumentException("权限组合过于复杂，请简化通配符");
            if(a.accepts(pair[0]) && !b.accepts(pair[1])) return false;
            for(char c:alphabet) {
                BitSet next=a.move(pair[0],c);
                if(!next.isEmpty()) queue.add(new BitSet[]{next,b.move(pair[1],c)});
            }
        }
        return true;
    }
}
