public class LemmaInfo implements Comparable<LemmaInfo> {
    Lemma lemma;
    int cnt;
    public LemmaInfo(Lemma lemma){
        this.lemma = lemma;
        cnt = 1;
    }

    @Override
    public String toString(){
        return lemma.init.t + ", " + lemma.init.grammemes.get(0) + ": " + cnt;
    }

    @Override
    public int compareTo(LemmaInfo o) {
        return o.cnt - cnt;
    }
}
